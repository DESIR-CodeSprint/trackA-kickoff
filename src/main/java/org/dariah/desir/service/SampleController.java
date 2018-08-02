package org.dariah.desir.service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dariah.desir.data.DisambiguatedAuthor;
import org.dariah.desir.data.OverlayResponse;
import org.dariah.desir.data.Page;
import org.dariah.desir.data.ResolvedCitation;
import org.dariah.desir.grobid.AuthorDisambiguationClient;
import org.dariah.desir.grobid.GrobidClient;
import org.dariah.desir.grobid.GrobidParsers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class SampleController {

    private static final String template = "Hello, %s!";

    @RequestMapping("/version")
    public String getVersion() {
        return "0.1.0";
    }

    @Autowired
    private GrobidParsers grobidParsers;

    @Autowired
    private GrobidClient grobidClient;

    @Autowired
    private EntityFishingService entityFishingService;

    @Autowired
    private AuthorDisambiguationClient authorDisambiguationClient;


    @RequestMapping(value = "/process", method = RequestMethod.POST, produces = "application/json")
    public OverlayResponse processPdf(@RequestParam(value = "file") MultipartFile pdf) {

        OverlayResponse response = null;
        String resultEntityFishing = null;
        try {
            InputStream input = pdf.getInputStream();

            final File tempFile = File.createTempFile("prefix", "suffix");
            tempFile.deleteOnExit();

            FileUtils.copyToFile(input, tempFile);

//            System.out.println("entity-fishing");
//            resultEntityFishing = this.entityFishingService.pdfProcessing(IOUtils.toBufferedInputStream(new FileInputStream(tempFile)));

            System.out.println("Grobid");
            String resultGrobid = grobidClient.processFulltextDocument(IOUtils.toBufferedInputStream(new FileInputStream(tempFile)));
            System.out.println(resultGrobid);

            System.out.println("Author disambiguation");
            String resultDisambiguation = authorDisambiguationClient.disambiguate(IOUtils.toInputStream(resultGrobid, StandardCharsets.UTF_8), "filename.xml");

            final Page pageDimension = grobidClient.getPageDimension(IOUtils.toBufferedInputStream(new FileInputStream(tempFile)));

            final List<DisambiguatedAuthor> disambiguatedAuthors = grobidParsers.processAffiliations(IOUtils.toInputStream(resultDisambiguation, StandardCharsets.UTF_8));
            final List<ResolvedCitation> resolvedCitations = grobidParsers.processCitations(IOUtils.toInputStream(resultGrobid, StandardCharsets.UTF_8));
            response = new OverlayResponse(disambiguatedAuthors, resolvedCitations);
            response.setPageDimention(pageDimension);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }

    @RequestMapping(value = "/entity_fishing", method = RequestMethod.POST, produces = "application/json")
    public String getEntityFishingResult(@RequestParam(value = "file") MultipartFile pdf) {

        String result = null;
        try {
            result = this.grobidClient.processFulltextDocument(pdf.getInputStream());

            result = this.grobidParsers.processAbstract(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)));
            result = this.entityFishingService.rawAbstractProcessing(result);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "{\"result\":" + result + "}";
    }
}
