package org.nuxeo.ecm.core.io.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
public class TestDownloadBlobInfo {

    @Test
    public void testParseDownloadPath() {
        assertParsed("", null, null);
        assertParsed("/", null, null);
        assertParsed("/myPath", "myPath", null);
        assertParsed("/myPath/", "myPath", null);
        assertParsed("/myPath/fileName", "myPath", "fileName");
        assertParsed("/my/path/fileName", "my/path", "fileName");
        assertParsed("/also/my/path/fileName", "also/my/path", "fileName");
        assertParsed("/file:content", "file:content", null);
        assertParsed("/file:content/file.txt", "file:content", "file.txt");
        assertParsed("/files:files/0/file", "files:files/0/file", null);
        assertParsed("/files:files/0/file/image.png", "files:files/0/file", "image.png");
    }

    protected void assertParsed(String xPathAndFileName, String xPath, String fileName) {
        DownloadBlobInfo downloadBlobInfo = new DownloadBlobInfo("repo/docPath" + xPathAndFileName);
        assertEquals("repo", downloadBlobInfo.getRepository());
        assertEquals("docPath", downloadBlobInfo.getDocId());
        assertEquals(xPath, downloadBlobInfo.getXpath());
        assertEquals(fileName, downloadBlobInfo.getFilename());
    }

    }

    @Test
    public void testFailNonExistingDocumentDownloadBlobInfo() {
        assertThrows(IllegalArgumentException.class, () -> new DownloadBlobInfo("nonExisting"));
    }

}
