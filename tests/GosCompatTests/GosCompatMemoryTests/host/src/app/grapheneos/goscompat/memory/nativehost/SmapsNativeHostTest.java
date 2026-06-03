package app.grapheneos.goscompat.memory.nativehost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.server.os.TombstoneProtos;
import com.android.server.os.TombstoneProtos.Tombstone;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SmapsNativeHostTest extends BaseHostJUnit4Test {
    private static final String RUNNER = "/data/local/tmp/goscompat_smaps_native_runner";
    private static final String TOMBSTONE_DIR = "/data/tombstones";
    private static final long TOMBSTONE_WAIT_TIMEOUT_MILLIS = 5_000;
    private static final long TOMBSTONE_WAIT_STEP_MILLIS = 200;
    private static final int SIGABRT_EXIT_CODE = 128 + 6;
    private static final String ALLOCATOR_HARDENED_MALLOC = "hardened_malloc";
    private static final String ALLOCATOR_HARDENED_MALLOC_DISABLED =
            "hardened_malloc_disabled";
    private static final String SIMPLE_GUARD_ABORT_PREFIX =
            "simple_smaps_parser guard abort after element allocation guard";
    private static final int MAX_MAPPING_SUMMARY_ENTRIES = 12;
    private static final String TOMBSTONE_COLLECTION_LOG =
            "goscompat_smaps_tombstone_collection";

    @Rule
    public final TestLogData mLogs = new TestLogData();

    private String mToken;
    private String mTombstoneUnavailableMessage;

    @Before
    public void setUp() throws Exception {
        mToken = UUID.randomUUID().toString();
    }

    @After
    public void tearDown() throws Exception {
        if (mToken == null) {
            return;
        }
        if (mTombstoneUnavailableMessage != null) {
            return;
        }
        List<TombstoneArtifact> artifacts = listTokenTombstones(null);
        if (artifacts.isEmpty()) {
            return;
        }

        boolean disableAdbRootAfterCleanup = false;
        try {
            if (!getDevice().isAdbRoot()) {
                if (getDevice().enableAdbRoot()) {
                    disableAdbRootAfterCleanup = true;
                } else {
                    addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG + "_cleanup_root",
                            "unable to enable adb root for tombstone cleanup; cleanup will "
                                    + "continue without root\n");
                }
            }
        } catch (Exception e) {
            addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG + "_cleanup_root",
                    "unable to check or enable adb root for tombstone cleanup; cleanup will "
                            + "continue with the current shell state: "
                            + describeException(e) + "\n");
        }

        try {
            for (TombstoneArtifact artifact : artifacts) {
                deleteTombstoneFileBestEffort(artifact.path);
                deleteTombstoneFileBestEffort(stripProtoSuffix(artifact.path));
            }
        } finally {
            if (disableAdbRootAfterCleanup) {
                try {
                    if (!getDevice().disableAdbRoot()) {
                        addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG + "_cleanup_root",
                                "unable to disable adb root after tombstone cleanup\n");
                    }
                } catch (Exception e) {
                    addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG + "_cleanup_root",
                            "unable to disable adb root after tombstone cleanup: "
                                    + describeException(e) + "\n");
                }
            }
        }
    }

    @Test
    public void simpleSmapsElementAllocationsWithHardenedMallocDoesNotCrashFromNativeBinary()
            throws Exception {
        String testName =
                "simpleSmapsElementAllocationsWithHardenedMallocDoesNotCrashFromNativeBinary";
        assertNativeParserDoesNotCrash(ALLOCATOR_HARDENED_MALLOC, testName, false);
    }

    @Test
    public void simpleSmapsElementAllocationsWithoutHardenedMallocDoesNotCrashFromNativeBinary()
            throws Exception {
        String testName =
                "simpleSmapsElementAllocationsWithoutHardenedMallocDoesNotCrashFromNativeBinary";
        // Control: this test should pass.
        assertNativeParserDoesNotCrash(ALLOCATOR_HARDENED_MALLOC_DISABLED, testName, true);
    }

    private void assertNativeParserDoesNotCrash(String allocatorState, String testName,
            boolean disableHardenedMalloc) throws Exception {
        NativeRun run = runNativeParser(testName, disableHardenedMalloc);
        List<TombstoneArtifact> tombstones = waitForTombstones(testName, run.exitCode != 0);
        reportRunArtifacts(testName, allocatorState, run, tombstones);

        if (run.exitCode != 0) {
            if (mTombstoneUnavailableMessage == null) {
                assertFalse("native crash should produce a token-matched tombstone",
                        tombstones.isEmpty());
                String abortMessage = tombstones.get(0).tombstone.getAbortMessage();
                assertTrue("expected guard abort in native tombstone, got: " + abortMessage,
                        abortMessage.startsWith(SIMPLE_GUARD_ABORT_PREFIX));
                assertEquals("expected SIGABRT for guard abort",
                        SIGABRT_EXIT_CODE, run.exitCode);
            }
        }

        assertEquals(failureMessage(run, tombstones), 0, run.exitCode);
        assertTrue("unexpected tombstone from a successful native parser run",
                tombstones.isEmpty());
    }

    private NativeRun runNativeParser(String testName, boolean disableHardenedMalloc)
            throws Exception {
        String command = "sh -c 'echo $$; "
                + (disableHardenedMalloc
                        ? "export DISABLE_HARDENED_MALLOC=1; "
                        : "unset DISABLE_HARDENED_MALLOC; ")
                + "exec " + RUNNER + " " + testName + " " + mToken + "'";
        CommandResult result = getDevice().executeShellV2Command(command);
        assertNotNull("native command exit code", result.getExitCode());
        return new NativeRun(command, result, result.getExitCode());
    }

    private List<TombstoneArtifact> waitForTombstones(String testName, boolean expectTombstone)
            throws Exception {
        if (mTombstoneUnavailableMessage != null) {
            return new ArrayList<>();
        }
        long deadline = System.currentTimeMillis() + TOMBSTONE_WAIT_TIMEOUT_MILLIS;
        List<TombstoneArtifact> tombstones;
        do {
            tombstones = listTokenTombstones(testName);
            if (!expectTombstone || !tombstones.isEmpty()) {
                return tombstones;
            }
            Thread.sleep(TOMBSTONE_WAIT_STEP_MILLIS);
        } while (System.currentTimeMillis() < deadline);
        return tombstones;
    }

    private List<TombstoneArtifact> listTokenTombstones(String testName) throws Exception {
        List<TombstoneArtifact> matches = new ArrayList<>();
        if (mTombstoneUnavailableMessage != null) {
            return matches;
        }
        String[] tombstones;
        try {
            tombstones = getDevice().getChildren(TOMBSTONE_DIR);
        } catch (Exception e) {
            recordTombstonesUnavailable(
                    "unable to list " + TOMBSTONE_DIR + ": " + describeException(e));
            return matches;
        }
        if (tombstones == null) {
            recordTombstonesUnavailable("unable to list " + TOMBSTONE_DIR);
            return matches;
        }

        for (String tombstone : tombstones) {
            if (!tombstone.endsWith(".pb")) {
                continue;
            }
            String path = TOMBSTONE_DIR + "/" + tombstone;
            TombstoneArtifact artifact;
            try {
                artifact = parseTombstone(path);
            } catch (Exception e) {
                recordTombstonesUnavailable(
                        "unable to pull or parse " + path + ": " + describeException(e));
                return matches;
            }
            List<String> commandLine = artifact.tombstone.getCommandLineList();
            if (!contains(commandLine, mToken)) {
                continue;
            }
            if (testName != null && !contains(commandLine, testName)) {
                continue;
            }
            matches.add(artifact);
        }
        return matches;
    }

    private TombstoneArtifact parseTombstone(String path) throws Exception {
        File file = getDevice().pullFile(path);
        byte[] proto = Files.readAllBytes(file.toPath());
        try (InputStream input = new FileInputStream(file)) {
            return new TombstoneArtifact(path, Tombstone.parseFrom(input), proto);
        } finally {
            file.delete();
        }
    }

    private void deleteTombstoneFileBestEffort(String path) {
        try {
            getDevice().deleteFile(path);
            if (getDevice().doesFileExist(path)) {
                addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG + "_cleanup_" + sanitize(path),
                        "tombstone collection succeeded, but cleanup could not delete "
                                + path + "; this does not affect the test result\n");
            }
        } catch (Exception e) {
            addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG + "_cleanup_" + sanitize(path),
                    "tombstone collection succeeded, but cleanup could not delete "
                            + path + "; this does not affect the test result: "
                            + describeException(e) + "\n");
        }
    }

    private void reportRunArtifacts(String testName, String allocatorState,
            NativeRun run, List<TombstoneArtifact> tombstones) throws Exception {
        String baseName = "goscompat_smaps_native_" + sanitize(allocatorState) + "_"
                + sanitize(testName);
        String commandLog = "command=" + run.command + "\n"
                + "exitCode=" + run.exitCode + "\n"
                + tombstoneCollectionStatus()
                + "stdout:\n" + emptyToMarker(run.result.getStdout())
                + "\nstderr:\n" + emptyToMarker(run.result.getStderr());
        addTextLog(baseName + "_command", commandLog);

        for (int i = 0; i < tombstones.size(); i++) {
            TombstoneArtifact artifact = tombstones.get(i);
            String tombstoneName = baseName + "_tombstone_" + i;
            addTextLog(tombstoneName + "_text", formatTombstone(artifact.tombstone));
            try (ByteArrayInputStreamSource source =
                    new ByteArrayInputStreamSource(artifact.proto)) {
                mLogs.addTestLog(tombstoneName + "_pb", LogDataType.PB, source);
            }
        }
    }

    private void addTextLog(String name, String text) throws Exception {
        try (ByteArrayInputStreamSource source =
                new ByteArrayInputStreamSource(text.getBytes(StandardCharsets.UTF_8))) {
            mLogs.addTestLog(name, LogDataType.TEXT, source);
        }
    }

    private String failureMessage(NativeRun run, List<TombstoneArtifact> tombstones) {
        StringBuilder builder = new StringBuilder();
        builder.append("native parser command should not crash")
                .append("\ncommand=").append(run.command)
                .append("\nexitCode=").append(run.exitCode)
                .append('\n').append(tombstoneCollectionStatus())
                .append("stdout:\n").append(emptyToMarker(run.result.getStdout()))
                .append("\nstderr:\n").append(emptyToMarker(run.result.getStderr()));
        for (TombstoneArtifact artifact : tombstones) {
            builder.append("\n\n").append(formatTombstone(artifact.tombstone));
        }
        return builder.toString();
    }

    private String tombstoneCollectionStatus() {
        if (mTombstoneUnavailableMessage == null) {
            return "nativeTombstones=available\n";
        }
        return "nativeTombstones=unavailable: " + mTombstoneUnavailableMessage + "\n";
    }

    private void recordTombstonesUnavailable(String message) {
        if (mTombstoneUnavailableMessage != null) {
            return;
        }
        mTombstoneUnavailableMessage = message;
        addTextLogBestEffort(TOMBSTONE_COLLECTION_LOG, message + "\n");
    }

    private void addTextLogBestEffort(String name, String text) {
        System.out.println(name + ": " + text.trim());
        try {
            addTextLog(name, text);
        } catch (Exception e) {
            System.out.println(name + " log unavailable: " + describeException(e));
        }
    }

    private static String formatTombstone(Tombstone tombstone) {
        StringBuilder builder = new StringBuilder();
        builder.append("path commandLine=")
                .append(tombstone.getCommandLineList())
                .append("\npid=").append(tombstone.getPid())
                .append(", tid=").append(tombstone.getTid())
                .append(", uid=").append(tombstone.getUid())
                .append(", processUptime=").append(tombstone.getProcessUptime())
                .append("\nsignal ")
                .append(tombstone.getSignalInfo().getNumber())
                .append(" (").append(tombstone.getSignalInfo().getName()).append(")")
                .append(", code ")
                .append(tombstone.getSignalInfo().getCode())
                .append(" (").append(tombstone.getSignalInfo().getCodeName()).append(")")
                .append("\nabort message: ").append(tombstone.getAbortMessage())
                .append("\nmemory mappings:\n")
                .append(formatMemoryMappingSummary(tombstone))
                .append("\nbacktrace:\n");

        TombstoneProtos.Thread thread = tombstone.getThreadsMap().get(tombstone.getTid());
        if (thread == null) {
            builder.append("  <no crashing thread>\n");
            return builder.toString();
        }
        for (int i = 0; i < thread.getCurrentBacktraceList().size(); i++) {
            TombstoneProtos.BacktraceFrame frame = thread.getCurrentBacktraceList().get(i);
            builder.append('#').append(i)
                    .append(' ')
                    .append(frame.getFileName())
                    .append(" (")
                    .append(frame.getFunctionName())
                    .append(")\n");
        }
        return builder.toString();
    }

    private static String formatMemoryMappingSummary(Tombstone tombstone) {
        List<TombstoneProtos.MemoryMapping> mappings = tombstone.getMemoryMappingsList();
        if (mappings.isEmpty()) {
            return "  <no memory mappings>\n";
        }

        Map<String, Integer> counts = new HashMap<>();
        for (TombstoneProtos.MemoryMapping mapping : mappings) {
            String name = mapping.getMappingName();
            if (name.isEmpty()) {
                name = "<anonymous>";
            }
            Integer count = counts.get(name);
            counts.put(name, count == null ? 1 : count + 1);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int byCount = Integer.compare(right.getValue(), left.getValue());
            return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
        });

        StringBuilder builder = new StringBuilder();
        builder.append("  total=").append(mappings.size()).append('\n');
        int entryCount = Math.min(entries.size(), MAX_MAPPING_SUMMARY_ENTRIES);
        for (int i = 0; i < entryCount; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            builder.append("  ")
                    .append(entry.getValue())
                    .append(' ')
                    .append(entry.getKey())
                    .append('\n');
        }
        if (entries.size() > MAX_MAPPING_SUMMARY_ENTRIES) {
            builder.append("  ... ")
                    .append(entries.size() - MAX_MAPPING_SUMMARY_ENTRIES)
                    .append(" more mapping names\n");
        }
        return builder.toString();
    }

    private static boolean contains(List<String> values, String needle) {
        for (String value : values) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String stripProtoSuffix(String path) {
        return path.endsWith(".pb") ? path.substring(0, path.length() - 3) : path;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String emptyToMarker(String value) {
        return value == null || value.isEmpty() ? "<empty>\n" : value;
    }

    private static String describeException(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            return e.getClass().getName();
        }
        return e.getClass().getName() + ": " + message;
    }

    private static final class NativeRun {
        final String command;
        final CommandResult result;
        final int exitCode;

        NativeRun(String command, CommandResult result, int exitCode) {
            this.command = command;
            this.result = result;
            this.exitCode = exitCode;
        }
    }

    private static final class TombstoneArtifact {
        final String path;
        final Tombstone tombstone;
        final byte[] proto;

        TombstoneArtifact(String path, Tombstone tombstone, byte[] proto) {
            this.path = path;
            this.tombstone = tombstone;
            this.proto = proto;
        }
    }
}
