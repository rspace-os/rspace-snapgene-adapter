package com.researchspace.snapgene.wclient;

import static com.researchspace.core.util.JacksonUtil.toJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.apiutils.rest.utils.SimpleResilienceFacade;

import io.vavr.control.Either;

/*
 * This mocks calls to the server and tests that we have configured
 * a retry and circuit breaker protection on the Snapgene service
 */
@ExtendWith(MockitoExtension.class)
public class SnapgeneServerResilienceTest {
	@Mock
	RestTemplate template;
	SnapgeneWSClientImpl wsClient;
	final int delayBetweenRetriesMillis = 10; // fast for testing

	@BeforeEach
	void before() throws URISyntaxException {
		SimpleResilienceFacade facade = new SimpleResilienceFacade(10, 20);
		wsClient = new SnapgeneWSClientImpl(new URI("http://somewhere.com"), template, facade,
				() -> "SnapgeneServerResilienceTest");
		wsClient.init();

	}

	@Test
	@DisplayName("3 attempts at API Call before failure")
	void failAfter3Retries() {
		// set sliding window to high value to stop circuit breaker
		wsClient.setFacade(new SimpleResilienceFacade(delayBetweenRetriesMillis, 200));
		mockApiThrowsRestServerError();

		Either<ApiError, String> resp = wsClient.status();
		Assertions.assertTrue(resp.isLeft());
		verifyNumApiCalls(3);
		// a subsequent call also has 3 retries, i.e each call is independent
		resp = wsClient.status();
		Assertions.assertTrue(resp.isLeft());
		verifyNumApiCalls(6);
	}

	@Test
	@DisplayName("Circuit breaker overrides retries")
	void circuitBreaker() {
		// set sliding window to low value to trigger circuit breaker after 10 calls(all
		// calls fail, so we have
		// 100% failure rate after 10 calls which is minimum required to collect stats
		wsClient.setFacade(new SimpleResilienceFacade(delayBetweenRetriesMillis, 10));

		// always throw an exception
		mockApiThrowsRestServerError();

		// for i = 1,2,3 there are 3 retries = 9 calls total
		// when i =4, there are now 10 failed calls - enough to trigger the circuit
		// breaker,
		// which will block any further api calls being made
		for (int i = 1; i <= 4; i++) {
			Either<ApiError, String> resp = wsClient.status();
			Assertions.assertTrue(resp.isLeft());
		}

		// this would be 12 if circuit breaker didn't kick in
		verifyNumApiCalls(10);
		Either<ApiError, String> resp = wsClient.status();
		// and the error message is propagated to the ApiError.
		assertThat(resp.getLeft().getErrors().get(0),
				containsString("CircuitBreaker 'snapgene' is OPEN and does not permit further calls"));
	}

	private void verifyNumApiCalls(int expected) {
		Mockito.verify(template, Mockito.times(expected)).getForEntity(Mockito.any(URI.class),
				Mockito.eq(String.class));
	}

	private void mockApiThrowsRestServerError() {
		ApiError error = apiError500();
		Mockito.when(template.getForEntity(Mockito.any(URI.class), Mockito.eq(String.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error",
						toJson(error).getBytes(), Charset.defaultCharset()));
	}

	ApiError apiError500() {
		return new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCodes.GENERAL_ERROR.getCode(), "server error",
				"excpetion message");
	}

}
