package com.researchspace.snapgene.wclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.core.util.JacksonUtil;

import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@ExtendWith(MockitoExtension.class)
class VavrTest {

	@Mock
	RestTemplate template;
	URI uri = null;

	@BeforeEach()
	void before() throws URISyntaxException {
		uri = new URI("http://somewhere.com");
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	class DataResponse {
		private Integer a, b;
	}

	@Test
	void testRestClientError() {
		mockApiThrowsRestClientError();
		Either<ApiError, DataResponse> resultEither = makeApiCall();
		Assertions.assertTrue(resultEither.isLeft());
		assertEquals(400, resultEither.getLeft().getHttpCode());
	}

	@Test
	void testRestServerError() {
		mockApiThrowsRestServerError();
		Either<ApiError, DataResponse> resultEither = makeApiCall();
		Assertions.assertTrue(resultEither.isLeft());
		assertEquals(500, resultEither.getLeft().getHttpCode());
	}

	@Test
	void testRestCallOK() {
		mockApiSuccess();
		Either<ApiError, DataResponse> resultEither = makeApiCall();
		Assertions.assertTrue(resultEither.isRight());
		assertEquals(5, resultEither.get().getB());
	}

	private void mockApiSuccess() {
		Mockito.when(template.getForEntity(uri, DataResponse.class))
				.thenReturn(ResponseEntity.ok().body(new DataResponse(2, 5)));

	}

	Either<ApiError, DataResponse> makeApiCall() {
		return Try.ofSupplier(this::doApiCall).toEither().mapLeft(this::fromException);
	}

	private void mockApiThrowsRestClientError() {
		ApiError error = apiError400();
		Mockito.when(template.getForEntity(uri, DataResponse.class)).thenThrow(new HttpClientErrorException(
				HttpStatus.BAD_REQUEST, "BadRequest", JacksonUtil.toJson(error).getBytes(), Charset.defaultCharset()));
	}

	private void mockApiThrowsRestServerError() {
		ApiError error = apiError500();
		Mockito.when(template.getForEntity(uri, DataResponse.class))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "server error",
						JacksonUtil.toJson(error).getBytes(), Charset.defaultCharset()));
	}

	private DataResponse doApiCall() {
		return template.getForEntity(uri, DataResponse.class).getBody();
	}

	ApiError fromException(Throwable e) {
		if (e instanceof HttpStatusCodeException) {
			HttpStatusCodeException ce = (HttpStatusCodeException) e;
			return JacksonUtil.fromJson(ce.getResponseBodyAsString(), ApiError.class);
		} else {
			throw new IllegalStateException("unsupported exception");
		}
	}

	ApiError apiError400() {
		return new ApiError(HttpStatus.BAD_REQUEST, ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(), "bad request",
				"exception message");
	}

	ApiError apiError500() {
		return new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCodes.GENERAL_ERROR.getCode(), "server error",
				"exception message");
	}
}
