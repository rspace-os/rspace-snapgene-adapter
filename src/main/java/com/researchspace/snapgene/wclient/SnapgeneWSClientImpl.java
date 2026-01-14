package com.researchspace.snapgene.wclient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.rest.utils.SimpleResilienceFacade;
import com.researchspace.zmq.snapgene.requests.ExportDnaFileConfig;
import com.researchspace.zmq.snapgene.requests.GeneratePngMapConfig;
import com.researchspace.zmq.snapgene.requests.GenerateSVGMapConfig;
import com.researchspace.zmq.snapgene.requests.ImportDnaFileConfig;
import com.researchspace.zmq.snapgene.requests.ReportEnzymesConfig;
import com.researchspace.zmq.snapgene.requests.ReportORFsConfig;
import com.researchspace.zmq.snapgene.responses.SnapgeneResponse;

import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes calls to a Snapgene webservice. <br>
 * This implementation uses Retry and CircuitBreaker around API calls.
 */
@Slf4j
public class SnapgeneWSClientImpl implements SnapgeneWSClient {

	public void setFacade(SimpleResilienceFacade facade) {
		this.facade = facade;
	}

	private URI snapgeneServerUrl;
	private URI statusUri;
	private URI exportSvgUri;
	private URI exportPngUri;
	private URI importDnaFile;
	private URI exportDnaFile;
	private URI reportEnzymes;
	private URI reportORFs;

	private RestTemplate template;
	private SimpleResilienceFacade facade;
	private String customerID = "UNDEFINED_CUSTOMER";
	private Supplier<String> customerIDSupplier;
	private static final int CONNECTION_TIMEOUT = 2000;

	public SnapgeneWSClientImpl(URI url, Supplier<String> customerIDSupplier) {
		this(url, createRestTemplate(), customerIDSupplier);
	}

	private static RestTemplate createRestTemplate() {
		RestTemplate template = new RestTemplate();
		SimpleClientHttpRequestFactory rf = (SimpleClientHttpRequestFactory) template.getRequestFactory();
		rf.setConnectTimeout(CONNECTION_TIMEOUT);
		return template;
	}

	/*
	 * for testing, allowing setting of mock template
	 */
	SnapgeneWSClientImpl(URI url, RestTemplate restTemplate, Supplier<String> customerIDSupplier) {
		this(url, restTemplate, new SimpleResilienceFacade(1000, 50), customerIDSupplier);
	}

	/*
	 * for testing, allowing setting of mock template and resilience facade
	 */
	SnapgeneWSClientImpl(URI url, RestTemplate restTemplate, SimpleResilienceFacade facade,
			Supplier<String> customerIDSupplier) {
		this.snapgeneServerUrl = url;
		this.template = restTemplate;
		this.facade = facade;
		this.customerIDSupplier = customerIDSupplier;
	}

	@PostConstruct
	public void init() {
		statusUri = doBuild("status");
		exportSvgUri = doBuild("exportSvg");
		exportPngUri = doBuild("exportPng");
		importDnaFile = doBuild("importDNAFile");
		exportDnaFile = doBuild("exportDNAFile");
		reportEnzymes = doBuild("reportEnzymes");
		reportORFs = doBuild("reportORFs");
		if (StringUtils.isBlank(customerID) || customerID.equals("UNDEFINED_CUSTOMER")) {
			this.customerID = customerIDSupplier.get();
		}
	}

	private URI doBuild(String path) {
		return baseUri().path("/snapgene/" + path).build().encode().toUri();
	}

	private UriComponentsBuilder baseUri() {
		return UriComponentsBuilder.fromUri(snapgeneServerUrl);
	}

	@Override
	public Either<ApiError, String> status() {
		return makeApiCall(() -> template.getForEntity(statusUri, String.class));
	}

	/**
	 * Uploads the file to Snapgene server and performs export to SVG.
	 * 
	 * @param config optional GenerateSVGMapConfig
	 * @return A ResponseEntity<SnapgeneResponse> with output file name
	 *         getOutputFileName() set if successful.
	 */
	@Override
	public Either<ApiError, SnapgeneResponse> convertToSvgFile(File file, GenerateSVGMapConfig config) {
		LinkedMultiValueMap<String, Object> map = createFileMap(file, config);
		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = createFilePostRequestEntity(map);
		return makeApiCall(
				() -> template.exchange(exportSvgUri, HttpMethod.POST, requestEntity, SnapgeneResponse.class));
	}

	/**
	 * Uploads the file to Snapgene server and performs export to PNG.
	 * 
	 * @return A ResponseEntity<SnapgeneResponse> with output file name
	 *         getOutputFileName() set if successful
	 */
	@Override
	public Either<ApiError, SnapgeneResponse> convertToPngFile(File file, GeneratePngMapConfig config) {
		LinkedMultiValueMap<String, Object> map = createFileMap(file, config);
		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = createFilePostRequestEntity(map);
		return makeApiCall(
				() -> template.exchange(exportPngUri, HttpMethod.POST, requestEntity, SnapgeneResponse.class));
	}

	/**
	 * Uploads a file to Snapgene server calculates enzymes
	 * 
	 * @return Either<ApiError, String> with output file name getOutputFileName()
	 *         set if successful
	 */
	@Override
	public Either<ApiError, String> enzymes(File file, ReportEnzymesConfig config) {
		Either<ApiError, File> nativeDnaFile = convertToNativeFileIfNeeded(file);
		if (nativeDnaFile.isLeft()) {
			return Either.left(nativeDnaFile.getLeft());
		}
		LinkedMultiValueMap<String, Object> map = createFileMap(nativeDnaFile.get(), config);
		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = createFilePostRequestEntity(map);
		return makeApiCall(() -> template.exchange(reportEnzymes, HttpMethod.POST, requestEntity, String.class));
	}

	/**
	 * Uploads a file to Snapgene server calculates enzymes
	 * 
	 * @return Either<ApiError, String> with output file name getOutputFileName()
	 *         set if successful
	 */
	@Override
	public Either<ApiError, String> orfs(File file, ReportORFsConfig config) {
		Either<ApiError, File> nativeDnaFile = convertToNativeFileIfNeeded(file);
		if (nativeDnaFile.isLeft()) {
			return Either.left(nativeDnaFile.getLeft());
		}
		LinkedMultiValueMap<String, Object> map = createFileMap(nativeDnaFile.get(), config);
		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = createFilePostRequestEntity(map);
		return makeApiCall(() -> template.exchange(reportORFs, HttpMethod.POST, requestEntity, String.class));
	}

	private Either<ApiError, File> convertToNativeFileIfNeeded(File file) {
		return Try.ofCallable(() -> doConvertToNativeFileIfRequired(file))
				.getOrElseGet(e -> Either.left(new ApiError(HttpStatus.BAD_REQUEST, 400, e.getMessage(),
						"Could not convert file to native .dna file - IO exception before sending")));
	}

	/**
	 * Uploads the .dna file to Snapgene server and converts it to a format.
	 * It may convert to native Snapgene .dna format as an intermediate step.
	 * 
	 * @return A ResponseEntity<SnapgeneResponse> with output file name
	 *         getOutputFileName() set if successful
	 */
	@Override
	public Either<ApiError, SnapgeneResponse> exportDnaFile(File file, ExportDnaFileConfig config) {
		Either<ApiError, File> nativeDnaFile = convertToNativeFileIfNeeded(file);
		if (nativeDnaFile.isLeft()) {
			return Either.left(nativeDnaFile.getLeft());
		}
		LinkedMultiValueMap<String, Object> map = createFileMap(nativeDnaFile.get(), config);
		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = createFilePostRequestEntity(map);
		return makeApiCall(
				() -> template.exchange(exportDnaFile, HttpMethod.POST, requestEntity, SnapgeneResponse.class));
	}

	/**
	 * Uploads the file to Snapgene server and imports as DNA file.
	 * 
	 * @return Either<ApiError, SnapgeneResponse> with output .dna file name
	 *         getOutputFileName() set if successful
	 */
	@Override
	public Either<ApiError, SnapgeneResponse> importDnaFile(File file) {
		LinkedMultiValueMap<String, Object> map = createFileMap(file, buildImportDnaConfig());
		HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = createFilePostRequestEntity(map);
		return makeApiCall(
				() -> template.exchange(importDnaFile, HttpMethod.POST, requestEntity, SnapgeneResponse.class));
	}

	@Override
	public Either<ApiError, byte[]> downloadFile(String outputFileName) {
		URI uri = baseUri().path("/snapgene/downloadFile").queryParam("fileName", outputFileName)
				.queryParam("customerId", customerID).build().encode().toUri();
		return makeApiCall(() -> template.getForEntity(uri, byte[].class));
	}

	/**
	 * Facade method which uploads file, converts to DNA if necessary then generates
	 * a PNG file and downloads it. <br>
	 * This facade method makes several API calls, returning after the first
	 * failure.
	 * 
	 * @param fileToConvert Any DNA file acceptable for importDNAFile (fasta,
	 *                       genbank etc) or a native .dna file.
	 * @return Either<ApiError, byte[]>
   */
	@Override
	public Either<ApiError, byte[]> uploadAndDownloadPng(File fileToConvert, GeneratePngMapConfig pngConfig)
			throws FileNotFoundException, IOException {
		Either<ApiError, File> nativeDnaFile = doConvertToNativeFileIfRequired(fileToConvert);
		if (nativeDnaFile.isLeft()) {
			return Either.left(nativeDnaFile.getLeft());
		}
		Either<ApiError, SnapgeneResponse> convertToPngResponse = convertToPngFile(nativeDnaFile.get(), pngConfig)
				.peek(r -> downloadFile(r.getOutputFileName()));
		if (convertToPngResponse.isLeft()) {
			log.warn("Conversion to PNG failed - {}", convertToPngResponse.getLeft().getMessage());
			return convertToPngResponse.map(sr -> new byte[0]);
		} else {
			String pngFileName = convertToPngResponse.get().getOutputFileName();
			return downloadFile(pngFileName);
		}

	}

	// if is not already a native file, will convert to .dna and return a .dna file.
	private Either<ApiError, File> doConvertToNativeFileIfRequired(File fileToConvert)
			throws IOException {
		File nativeDnaFile;
		if (isAlreadyANativeSnapgeneFile(fileToConvert)) {
			log.info("{} is already a .dna file", fileToConvert.getAbsolutePath());
			nativeDnaFile = fileToConvert;
		} else {
			Either<ApiError, SnapgeneResponse> resp = importDnaFile(fileToConvert);
			if (resp.isLeft()) {
				log.warn("Importing non-native file failed - {}", resp.getLeft().getMessage());
				return Either.left(resp.getLeft());
			}

			String dnaOutfileName = resp.get().getOutputFileName();
			Either<ApiError, byte[]> nativeDnaFileEither = downloadFile(dnaOutfileName);

			if (nativeDnaFileEither.isLeft()) {
				log.warn("Downloading converted file failed - {}", nativeDnaFileEither.getLeft().getMessage());
				return Either.left(nativeDnaFileEither.getLeft());
			} else {
				FileUtils.getTempDirectory();
				nativeDnaFile = new File(FileUtils.getTempDirectory(), dnaOutfileName);
				IOUtils.write(nativeDnaFileEither.get(), new FileOutputStream(nativeDnaFile));
			}
		}
		return Either.right(nativeDnaFile);
	}

	private boolean isAlreadyANativeSnapgeneFile(File fileToConvert) {
		return FilenameUtils.getExtension(fileToConvert.getName()).equalsIgnoreCase("dna");
	}

	private HttpEntity<LinkedMultiValueMap<String, Object>> createFilePostRequestEntity(
			LinkedMultiValueMap<String, Object> map) {
		return new HttpEntity<>(map);
	}

	private LinkedMultiValueMap<String, Object> createFileMap(File file, Object config) {
		LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("file", new FileSystemResource(file.getAbsolutePath()));
		map.add("cfg", config);
		map.add("customerId", customerID);
		return map;
	}

	private ImportDnaFileConfig buildImportDnaConfig() {
		return ImportDnaFileConfig.builder().build();
	}

	private <T> Either<ApiError, T> makeApiCall(Supplier<ResponseEntity<T>> restClient) {
		return facade.makeApiCall(restClient);
	}

}
