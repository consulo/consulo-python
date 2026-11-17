package com.intellij.python.requirements.parser;

import java.util.List;

public final class RequirementsParserHelper {
    /**
     * List of supported VCS URL schemes.
     */
    public static final List<String> VCS_SCHEMES = List.of(
        "git",
        "git+https",
        "git+ssh",
        "git+git",
        "hg+http",
        "hg+https",
        "hg+static-http",
        "hg+ssh",
        "svn",
        "svn+svn",
        "svn+http",
        "svn+https",
        "svn+ssh",
        "bzr+http",
        "bzr+https",
        "bzr+ssh",
        "bzr+sftp",
        "bzr+ftp",
        "bzr+lp"
    );

    private RequirementsParserHelper() {
    }
}
