package com.khadat.cinema.integration;

import com.khadat.cinema.controller.HealthController;
import com.khadat.cinema.exception.BusinessException;
import com.khadat.cinema.exception.GlobalExceptionHandler;
import com.khadat.cinema.repository.DatabaseProbeRepository;
import com.khadat.cinema.service.HealthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.ZoneId;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FoundationErrorHandlingTest {
    private MockMvc mvc;
    private DatabaseProbeRepository probe;

    @BeforeEach
    void setUp() {
        probe = mock(DatabaseProbeRepository.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new HealthController(new HealthService(probe)), new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler(Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"))))
            .setValidator(validator).build();
    }

    @Test void healthUsesDatabaseAndReturnsOnlyPublicStatus() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.database").value("UP"))
            .andExpect(jsonPath("$.*",hasSize(2)));
        verify(probe).checkConnection();
    }

    @Test void databaseFailureIs503WithoutCredentialsSqlOrTrace() throws Exception {
        doThrow(new DataAccessResourceFailureException("jdbc:mysql://root:secret SELECT roles secret"))
            .when(probe).checkConnection();
        mvc.perform(get("/api/health")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
            .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.path").value("/api/health"))
            .andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(content().string(not(containsString("secret"))))
            .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test void validationReturnsErrorsPerFieldWithoutRejectedValue() throws Exception {
        mvc.perform(post("/_test/body").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.title").isArray());
    }

    @Test void malformedJsonIs400() throws Exception {
        mvc.perform(post("/_test/body").contentType(MediaType.APPLICATION_JSON).content("{broken secret"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(content().string(not(containsString("secret"))));
    }

    @Test void wrongParameterTypeIs400() throws Exception {
        mvc.perform(get("/_test/parameter").param("id","secret"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(content().string(not(containsString("secret"))));
    }

    @Test void missingParameterIs400() throws Exception {
        mvc.perform(get("/_test/parameter")).andExpect(status().isBadRequest());
    }

    @Test void businessConflictIs409() throws Exception {
        mvc.perform(get("/_test/conflict")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TEST_CONFLICT"));
    }

    @Test void integrityConflictDoesNotExposeSql() throws Exception {
        mvc.perform(get("/_test/integrity")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
            .andExpect(content().string(not(containsString("INSERT"))));
    }

    @Test void unexpectedFailureIsSanitized500() throws Exception {
        mvc.perform(get("/_test/failure")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(not(containsString("secret"))));
    }

    @Test void unsupportedMethodIs405() throws Exception {
        mvc.perform(post("/api/health")).andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test void unsupportedContentTypeIs415() throws Exception {
        mvc.perform(post("/_test/body").contentType(MediaType.TEXT_PLAIN).content("data"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test void unsupportedAcceptStillReturnsUnifiedError() throws Exception {
        mvc.perform(get("/api/health").accept(MediaType.TEXT_HTML))
            .andExpect(status().isNotAcceptable()).andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    // Test-only request paths to exercise the shared MVC infrastructure.
    // These are never component-scanned into the packaged application.
    @RestController
    static class ProbeController {
        record Body(@NotBlank String title) { }
        @PostMapping("/_test/body") void body(@Valid @RequestBody Body body) { }
        @GetMapping("/_test/parameter") void parameter(@RequestParam int id) { }
        @GetMapping("/_test/conflict") void conflict() { throw new BusinessException("TEST_CONFLICT","Xung đột thử nghiệm."); }
        @GetMapping("/_test/integrity") void integrity() { throw new DataIntegrityViolationException("INSERT secret"); }
        @GetMapping("/_test/failure") void failure() { throw new IllegalStateException("secret"); }
    }
}
