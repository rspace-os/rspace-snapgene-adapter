package com.researchspace.snapgene.wclient;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.researchspace.apiutils.ApiError;
import com.researchspace.zmq.snapgene.requests.EnzymeSet;
import com.researchspace.zmq.snapgene.requests.ExportDnaFileConfig;
import com.researchspace.zmq.snapgene.requests.ExportFilter;
import com.researchspace.zmq.snapgene.requests.GeneratePngMapConfig;
import com.researchspace.zmq.snapgene.requests.GenerateSVGMapConfig;
import com.researchspace.zmq.snapgene.requests.ReadingFrame;
import com.researchspace.zmq.snapgene.requests.ReportEnzymesConfig;
import com.researchspace.zmq.snapgene.requests.ReportORFsConfig;
import com.researchspace.zmq.snapgene.responses.SnapgeneResponse;

import io.vavr.control.Either;
import lombok.extern.slf4j.Slf4j;

@ContextConfiguration(classes = SnapgeneTestConfig.class)
@TestPropertySource(locations = "classpath:/application.properties")
@Slf4j
@Ignore
public class SnapgeneWSClientITTest extends AbstractJUnit4SpringContextTests {

	private @Autowired SnapgeneWSClient client;
	File testNativeDnaFile = new File("src/test/resources/alpha-2-macroglobulin.dna");
	File enzymeDnaFile = new File("src/test/resources/alpha-2-macroglobulin.dna");
	File testGenbank = new File("src/test/resources/alpha-2-macroglobulin.gb");
	File pUC19fasta = new File("src/test/resources/pUC19.fasta");
	File testEmbl = new File("src/test/resources/Embl.embl");
	File testAb1 = new File("src/test/resources/3730.ab1");
	File testLasergene = new File("src/test/resources/Lasergene.seq");
	File testSeqBuilder = new File("src/test/resources/SeqBuilder.sbd");
	File notADnaFile = new File("src/test/resources/not-a-dna-file");

	static File testArtifactFolder = new File("TestArtifacts");

	@BeforeClass
	public static void setupDownloadFolder() {
		if (!testArtifactFolder.exists()) {
			testArtifactFolder.mkdir();
		}
	}

	@AfterClass
	public static void after() throws IOException {
		if (testArtifactFolder.exists()) {
			FileUtils.forceDelete(testArtifactFolder);
		}
	}

	@Test
	public void status() {
		assertTrue(client.status().isRight());
	}

	@Test
	public void exportToSvg() throws IOException {
		GenerateSVGMapConfig svgMapConfig = GenerateSVGMapConfig.builder().linear(true).showEnzymes(true).build();
		Either<ApiError, SnapgeneResponse> resp = client.convertToSvgFile(testNativeDnaFile, svgMapConfig);
		assertThat(resp.isRight(), Matchers.is(true));
		retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(testNativeDnaFile, "svg"));
	}

	@Test
	public void exportToPng() throws IOException {
		GeneratePngMapConfig cfGeneratePngMapConfig = generateAPngConfig();
		Either<ApiError, SnapgeneResponse> resp = client.convertToPngFile(testNativeDnaFile, cfGeneratePngMapConfig);
		assertThat(resp.isRight(), Matchers.is(true));
		retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(testNativeDnaFile, "png"));
	}

	@Test
	public void exportToPngFail() {
		GeneratePngMapConfig cfGeneratePngMapConfig = generateAPngConfig();
		Either<ApiError, SnapgeneResponse> resp = client.convertToPngFile(notADnaFile, cfGeneratePngMapConfig);
		assertThat(resp.isLeft(), Matchers.is(true));
		assertThat(resp.getLeft().getErrors().get(0), containsString("cannot be resolved in the file system"));
	}

	private GeneratePngMapConfig generateAPngConfig() {
		return GeneratePngMapConfig.builder().linear(true).showEnzymes(true).build();
	}

	@Test
	public void uploadGenbankAndDownloadPng() throws IOException {
		Either<ApiError, byte[]> byteResponseEntity = client.uploadAndDownloadPng(testGenbank, generateAPngConfig());
		File out = new File(testArtifactFolder, uniqueFile(testGenbank, "png"));
		try (FileOutputStream fosFileOutputStream = new FileOutputStream(out)) {
			IOUtils.write(byteResponseEntity.get(), fosFileOutputStream);
		}
		assertThat(out.length(), greaterThan(0L));
	}

	@Test
	public void exportGenbankToFile() throws  IOException {
		Either<ApiError, SnapgeneResponse> resp = client.exportDnaFile(testGenbank,
				new ExportDnaFileConfig(ExportFilter.FASTA));
		assertTrue(resp.isRight());
		File fasta = retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(testGenbank, "fasta"));
		// assert that file is not empty.
		assertEquals(56695, fasta.length());
	}

	@Test
	public void exportFastaToGenbank() throws  IOException {
		Either<ApiError, SnapgeneResponse> resp = client.exportDnaFile(pUC19fasta,
				new ExportDnaFileConfig(ExportFilter.GENBANK_STANDARD));
		assertTrue(resp.isRight());
		File genbankF = retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(pUC19fasta, "gb"));
		// size seems to fluctuate and not looked into why, so test for a small range for now.
		assertTrue(genbankF.length() >= 4155 && genbankF.length() < 4160);
		String file = FileUtils.readFileToString(genbankF, Charset.defaultCharset());
		assertTrue(file.contains("LOCUS")); // is in genbank format
	}

	/**
	 * Uses BioJava to convert .ab1 files
	 * @since 0.0.7 server
	 */
	@Test

	public void exportAb1ToFile() throws IOException {
		Either<ApiError, SnapgeneResponse> resp = client.exportDnaFile(testAb1,
				new ExportDnaFileConfig(ExportFilter.FASTA));
		assertTrue(resp.isRight());
		File fasta = retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(testAb1, "fasta"));
		// assert that file is correct length
		assertEquals(1227, fasta.length()); //1165 seq, + header
	}

	@Test
	public void exportOtherFormatsToFile() throws IOException {
		for (File file : new File[] { testLasergene, testSeqBuilder, testEmbl }) {
			Either<ApiError, SnapgeneResponse> resp = client.exportDnaFile(file,
					new ExportDnaFileConfig(ExportFilter.FASTA));
			assertTrue(resp.isRight());
			File fasta = retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(file, "fasta"));
			// assert that file is not empty.
			assertTrue(fasta.length() > 100);
		}
		Either<ApiError, SnapgeneResponse> resp = client.exportDnaFile(testGenbank,
				new ExportDnaFileConfig(ExportFilter.FASTA));
		assertTrue(resp.isRight());
		File fasta = retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(testGenbank, "fasta"));
		// assert that file is not empty.
		assertEquals(56695, fasta.length());
	}

	@Test
	public void exportNativeDNAToFile() throws IOException {
		Either<ApiError, SnapgeneResponse> resp = client.exportDnaFile(enzymeDnaFile,
				new ExportDnaFileConfig(ExportFilter.FASTA));
		assertTrue(resp.isRight());
		File fasta = retrieveOutputFile(resp.get().getOutputFileName(), uniqueFile(enzymeDnaFile, "fasta"));
		// assert that file is not empty.
		assertEquals(56695, fasta.length());
	}

	@Test
	public void enzymes() {
		Either<ApiError, String> resp = client.enzymes(enzymeDnaFile,
				new ReportEnzymesConfig(EnzymeSet.UNIQUE_AND_DUAL));
		assertEnzymeResponse(resp);
	}

	@Test
	public void enzymesAndConvert() {
		Either<ApiError, String> resp = client.enzymes(testGenbank, new ReportEnzymesConfig(EnzymeSet.UNIQUE_AND_DUAL));
		assertEnzymeResponse(resp);
	}

	private void assertEnzymeResponse(Either<ApiError, String> resp) {
		assertThat(resp.get(), hasJsonPath("$.count", Matchers.equalTo(26)));
		assertThat(resp.get(), hasJsonPath("$.setName", Matchers.equalTo("Unique & Dual Cutters")));
	}

	@Test
	public void orfs() {
		Either<ApiError, String> resp = client.orfs(enzymeDnaFile,
				new ReportORFsConfig(ReadingFrame.FIRST_FORWARD_FRAME));
		assertOrfResponse(resp);
	}

	@Test
	public void orfsAndConvert() {
		Either<ApiError, String> resp = client.orfs(testGenbank,
				new ReportORFsConfig(ReadingFrame.FIRST_FORWARD_FRAME));
		assertOrfResponse(resp);
	}

	private void assertOrfResponse(Either<ApiError, String> resp) {
		assertThat(resp.get(), hasJsonPath("$.ORFs.length()", Matchers.equalTo(34)));
	}

	private File retrieveOutputFile(String fileNameToDownload, String fileNameTarget)
			throws IOException {

		Either<ApiError, byte[]> byteResponseEntity = client.downloadFile(fileNameToDownload);

		File outFile = new File(testArtifactFolder, fileNameTarget);
		log.info("writing file export to {}", outFile.getName());
		try (FileOutputStream fosFileOutputStream = new FileOutputStream(outFile)) {
			IOUtils.write(byteResponseEntity.get(), fosFileOutputStream);
		}
		return outFile;
	}

	private String uniqueFile(File file, String suffix) {
		return FilenameUtils.getBaseName(file.getName()) + "-"
				+ DateTimeFormatter.ofPattern("yy-MM-dd-HH:mm:ss").format(LocalDateTime.now()) + "." + suffix;
	}

}
