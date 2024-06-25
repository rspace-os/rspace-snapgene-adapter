package com.researchspace.snapgene.wclient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.researchspace.apiutils.ApiError;
import com.researchspace.zmq.snapgene.requests.ExportDnaFileConfig;
import com.researchspace.zmq.snapgene.requests.GeneratePngMapConfig;
import com.researchspace.zmq.snapgene.requests.GenerateSVGMapConfig;
import com.researchspace.zmq.snapgene.requests.ReportEnzymesConfig;
import com.researchspace.zmq.snapgene.requests.ReportORFsConfig;
import com.researchspace.zmq.snapgene.responses.SnapgeneResponse;

import io.vavr.control.Either;

/**
 * Interface for making calls to Snapgene webservice
 */
public interface SnapgeneWSClient {

	/**
	 * Uploads the file to Snapgene server and performs export to SVG.
	 * 
	 * @param config optional GenerateSVGMapConfig
	 * @return A ResponseEntity<SnapgeneResponse> with output file name
	 *         getOutputFileName() set if successful.
	 */
	Either<ApiError, SnapgeneResponse> convertToSvgFile(File file, GenerateSVGMapConfig config);

	/**
	 * Uploads the file to Snapgene server and performs export to PNG.
	 * 
	 * @return A ResponseEntity<SnapgeneResponse> with output file name
	 *         getOutputFileName() set if successful
	 */
	Either<ApiError, SnapgeneResponse> convertToPngFile(File file, GeneratePngMapConfig config);

	/**
	 * Uploads a file to Snapgene server calculates enzymes. IF the file is not a
	 * native .dna file conversion is also handled
	 * 
	 * @return Either<ApiError, String> with output file name getOutputFileName()
	 *         set if successful
	 */
	Either<ApiError, String> enzymes(File file, ReportEnzymesConfig config);

	/**
	 * Uploads a file to Snapgene server and reports on ORFs. IF the file is not a
	 * native .dna file conversion is also handled
	 * 
	 * @return Either<ApiError, String> with output file name getOutputFileName()
	 *         set if successful
	 */
	Either<ApiError, String> orfs(File file, ReportORFsConfig config);

	/**
	 * Uploads the .dna file to Snapgene server and exports to a format.
	 * 
	 * @return A ResponseEntity<SnapgeneResponse> with output file name
	 *         getOutputFileName() set if successful
	 */
	Either<ApiError, SnapgeneResponse> exportDnaFile(File file, ExportDnaFileConfig config);

	/**
	 * Uploads the file to Snapgene server and imports as DNA file.
	 * 
	 * @return Either<ApiError, SnapgeneResponse> with output .dna file name
	 *         getOutputFileName() set if successful
	 */
	Either<ApiError, SnapgeneResponse> importDnaFile(File file);

	/**
	 * Given a filename (obtained from a previous API call) retrieves the file.
	 *
   */
	Either<ApiError, byte[]> downloadFile(String outputFileName);

	/**
	 * Facade method which uploads file, converts to native .DNA format if
	 * necessary, then generates a PNG file and downloads it. <br>
	 * This facade method makes several API calls, returning immediately after any
	 * call fails.
	 * 
	 * @param fileToConvert Any DNA file acceptable for importDNAFile (fasta,
	 *                       genbank etc) or a native .dna file.
	 * @return Either<ApiError, byte[]>
   */
	Either<ApiError, byte[]> uploadAndDownloadPng(File fileToConvert, GeneratePngMapConfig pngConfig)
			throws FileNotFoundException, IOException;

	/**
	 * Checks status of Snapgene service. If running will return a JSON string of
	 * status data, else an ApiError object
	 *
   *
	 */
	Either<ApiError, String> status();

}