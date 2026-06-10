package org.aohp.agentdriver.ui.filebridge;

import java.io.File;

/** Filesystem actions used by the File Bridge file-manager UI. */
final class FileBridgeFileOperations {
    private FileBridgeFileOperations() {
    }

    static DeleteResult delete(File file) {
        DeleteResult result = new DeleteResult();
        deleteInto(file, result);
        return result;
    }

    private static void deleteInto(File file, DeleteResult result) {
        if (file == null || !file.exists()) {
            result.failed++;
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteInto(child, result);
                }
            }
        }
        if (file.delete()) {
            result.deleted++;
        } else {
            result.failed++;
        }
    }

    static final class DeleteResult {
        int deleted;
        int failed;

        boolean hasFailures() {
            return failed > 0;
        }
    }
}
