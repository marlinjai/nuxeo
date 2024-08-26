/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id: TestMimetypeSniffing.java 28493 2008-01-04 19:51:30Z sfermigier $
 */

package org.nuxeo.ecm.platform.mimetype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

import java.io.File;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.mimetype.service.MimetypeRegistryService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import net.sf.jmimemagic.Magic;
import net.sf.jmimemagic.MagicMatch;

/**
 * Test binary files sniff.
 *
 * @author <a href="mailto:ja@nuxeo.com">Julien Anguenot</a>
 * @author <a href="mailto:lg@nuxeo.com">Laurent Godard</a>
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.mimetype")
public class TestMimetypeSniffing {

    @Inject
    private MimetypeRegistry mimetypeRegistry;

    private static File getFileFromResource(String path) {
        // retrieves contextually the resource file and decode its path
        // returns the corresponding File Object
        return FileUtils.getResourceFileFromContext(path);
    }

    private static File getZeroesDocument() {
        return FileUtils.getResourceFileFromContext("test-data/zeroes");
    }

    @Test
    public void testZeroesDocumentFromFile() {
        assertEquals("application/octet-stream", mimetypeRegistry.getMimetypeFromFile(getZeroesDocument()));
    }

    private static File getTextDocument() {
        return FileUtils.getResourceFileFromContext("test-data/hello.txt");
    }

    @Test
    public void testTextDocumentFromFile() {
        assertEquals("text/plain", mimetypeRegistry.getMimetypeFromFile(getTextDocument()));
    }

    private static File getWordDocument() {
        return FileUtils.getResourceFileFromContext("test-data/hello.doc");
    }

    @Test
    public void testWordDocumentFromFile() {
        assertEquals("application/msword", mimetypeRegistry.getMimetypeFromFile(getWordDocument()));
    }

    private static File getExcelDocument() {
        return getFileFromResource("test-data/hello.xls");
    }

    public void xtestExcelDocumentFromFile() {
        assertEquals("application/vnd.ms-excel", mimetypeRegistry.getMimetypeFromFile(getExcelDocument()));
    }

    private static File getPowerpointDocument() {
        return getFileFromResource("test-data/hello.ppt");
    }

    public void xtestPowerpointDocumentFromFile() {
        assertEquals("application/vnd.ms-powerpoint", mimetypeRegistry.getMimetypeFromFile(getPowerpointDocument()));
    }

    // Zip file
    private static File getZipDocument() {
        return getFileFromResource("test-data/hello.zip");
    }

    @Test
    public void testZipDocumentFromFile() {
        assertEquals("application/zip", mimetypeRegistry.getMimetypeFromFile(getZipDocument()));
    }

    // Ms Office Visio
    @Test
    public void testVisioDocument() {
        assertEquals("application/visio", mimetypeRegistry.getMimetypeFromExtension("vsdx")); //  NOSONAR
        assertEquals("application/visio", mimetypeRegistry.getMimetypeFromExtension("vsd"));
        assertEquals("application/visio", mimetypeRegistry.getMimetypeFromExtension("vst"));
        assertEquals("application/visio", mimetypeRegistry.getMimetypeFromExtension("vst"));
        assertEquals("application/visio", mimetypeRegistry.getMimetypeFromFilename("test-data/hello.vsd"));
    }

    // CSV file
    @Test
    public void testCsvDocument() {
        assertEquals("text/csv", mimetypeRegistry.getMimetypeFromExtension("csv")); // NOSONAR
        assertEquals("text/csv", mimetypeRegistry.getMimetypeFromFilename("test-data/test.csv"));
        assertEquals("text/csv", mimetypeRegistry.getMimetypeFromFile(getFileFromResource("test-data/test.csv")));
    }

    // OpenDocument Spreadsheet
    private static File getODFspreadsheetDocument() {
        return getFileFromResource("test-data/hello.ods");
    }

    public void xtestODFspreadsheetDocumentFromFile() {
        assertEquals("application/vnd.oasis.opendocument.spreadsheet",
                mimetypeRegistry.getMimetypeFromFile(getODFspreadsheetDocument()));
    }

    // OpenDocument Presentation
    private static File getODFpresentationDocument() {
        return getFileFromResource("test-data/hello.odp");
    }

    public void xtestODFpresentationDocumentFromFile() {
        mimetypeRegistry = new MimetypeRegistryService();
        assertEquals("application/vnd.oasis.opendocument.presentation",
                mimetypeRegistry.getMimetypeFromFile(getODFpresentationDocument()));
    }

    // MSO 2003 XML Excel
    private static File getMso2003XmlExcelDocument() {
        return getFileFromResource("test-data/TestExcel2003AsXML.xml.txt");
    }

    public void xtestMso2003XmlExcelDocumentFromFile() {
        assertEquals("application/vnd.ms-excel", mimetypeRegistry.getMimetypeFromFile(getMso2003XmlExcelDocument()));
    }

    // MSO 2003 XML Word
    private static File getMso2003XmlWordDocument() {
        return getFileFromResource("test-data/TestWord2003AsXML.xml.txt");
    }

    public void xtestMso2003XmlWordDocumentFromFile() {
        assertEquals("application/msword", mimetypeRegistry.getMimetypeFromFile(getMso2003XmlWordDocument()));
    }

    // Pure XML Document
    private static File getXmlDocument() {
        return getFileFromResource("test-data/simple.xml");
    }

    @Test
    public void testXmlDocumentFromFile() {
        assertEquals("text/xml", mimetypeRegistry.getMimetypeFromFile(getXmlDocument()));
    }

    // NXP-32673
    // MIME type cannot be computed from file content by jMimeMagic, fall back on file extension, throwing if
    // unregistered.
    @Test
    public void testUnregisteredMimeType() {
        assertThrows(MimetypeNotFoundException.class,
                () -> mimetypeRegistry.getMimetypeFromFile(getFileFromResource("test-data/undefined-mime-type.opj")));
    }

    // NXP-32673
    // MIME type is computed from file content by jMimeMagic as an undefined marker: "???", fall back on
    // "application/octet-stream" to have a valid MIME type.
    @Test
    public void testUndefinedMimeType() {
        try (MockedStatic<Magic> magicMock = Mockito.mockStatic(Magic.class)) {
            MagicMatch match = new MagicMatch();
            match.setMimeType("???");
            magicMock.when(() -> Magic.getMagicMatch(any(File.class), anyBoolean(), anyBoolean())).thenReturn(match);
            assertEquals("application/octet-stream",
                    mimetypeRegistry.getMimetypeFromFile(getFileFromResource("test-data/undefined-mime-type.opj")));
        }
    }

    // OOo 1.x Writer
    private static File getOOowriterDocument() {
        return getFileFromResource("test-data/hello.sxw");
    }

    public void xtestOOowriterDocumentFromFile() {
        assertEquals("application/vnd.sun.xml.writer", mimetypeRegistry.getMimetypeFromFile(getOOowriterDocument()));
    }

    // OOo special EMF graphic file
    private static File getOOoEmfDocument() {
        return getFileFromResource("test-data/graphic_ooo.vclmtf");
    }

    public void xtestOOoEMFDocumentFromFile() {
        assertEquals("application/x-vclmtf", mimetypeRegistry.getMimetypeFromFile(getOOoEmfDocument()));
    }

    // EMF graphic file
    private static File getEmfDocument() {
        return getFileFromResource("test-data/graphic.emf");
    }

    public void xtestEMFDocumentFromFile() {
        assertEquals("application/x-emf", mimetypeRegistry.getMimetypeFromFile(getEmfDocument()));
    }

}
