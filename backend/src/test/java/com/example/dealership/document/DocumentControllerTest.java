package com.example.dealership.document;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    private static final String URL = "/api/vehicles/1/documents";
    private static final String NUMBER = "FV/2026/10/118";
    private static final String VALID_BODY = """
            {
              "type": "OC_POLICY",
              "number": "FV/2026/10/118",
              "issueDate": "2026-10-20",
              "expiryDate": "2027-10-19"
            }
            """;
    private static final String VERIFY_BODY = """
            {
              "status": "VERIFIED"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void findAllReturnsDocuments() throws Exception {
        when(documentService.findAll(1L)).thenReturn(List.of(response(DocumentStatus.PENDING)));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].vehicleId").value(1))
                .andExpect(jsonPath("$[0].type").value("OC_POLICY"))
                .andExpect(jsonPath("$[0].number").value(NUMBER))
                .andExpect(jsonPath("$[0].issueDate").value("2026-10-20"))
                .andExpect(jsonPath("$[0].expiryDate").value("2027-10-19"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void findAllReturnsNotFoundProblemWhenVehicleDoesNotExist() throws Exception {
        when(documentService.findAll(99L)).thenThrow(new NotFoundException("Vehicle with id 99 not found"));

        mockMvc.perform(get("/api/vehicles/99/documents"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Vehicle with id 99 not found"));
    }

    @Test
    void createReturnsCreatedDocument() throws Exception {
        when(documentService.create(eq(1L), any(DocumentRequest.class))).thenReturn(response(DocumentStatus.PENDING));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/vehicles/1/documents/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createReturnsNotFoundProblemWhenVehicleDoesNotExist() throws Exception {
        when(documentService.create(eq(99L), any(DocumentRequest.class)))
                .thenThrow(new NotFoundException("Vehicle with id 99 not found"));

        mockMvc.perform(post("/api/vehicles/99/documents").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Vehicle with id 99 not found"));
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorsWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalidBody()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("issueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a date in the past or in the present"))
                .andExpect(jsonPath("$.errors[1].field").value("number"))
                .andExpect(jsonPath("$.errors[1].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[2].field").value("type"))
                .andExpect(jsonPath("$.errors[2].message").value("must not be null"));
        verifyNoInteractions(documentService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenExpiryDateIsBeforeIssueDate() throws Exception {
        String body = VALID_BODY.replace("2027-10-19", "2026-10-19");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("expiryDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be before issue date"));
        verifyNoInteractions(documentService);
    }

    @Test
    void createReturnsEnglishValidationMessagesWhenAcceptLanguageIsPolish() throws Exception {
        String body = VALID_BODY.replace(NUMBER, " ");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "pl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
        verifyNoInteractions(documentService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenTypeIsUnknown() throws Exception {
        String body = VALID_BODY.replace("OC_POLICY", "PASSPORT");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be one of: SALES_AGREEMENT, INVOICE, COC, OC_POLICY, REGISTRATION_POA"));
        verifyNoInteractions(documentService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenValueHasWrongType() throws Exception {
        String body = VALID_BODY.replace("2026-10-20", "yesterday");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("issueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
        verifyNoInteractions(documentService);
    }

    @Test
    void createReturnsBadRequestProblemWhenBodyIsNotJson() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").value("request body must be valid JSON"));
        verifyNoInteractions(documentService);
    }

    @Test
    void createReturnsConflictProblemWhenActiveDocumentOfTypeAlreadyExists() throws Exception {
        when(documentService.create(eq(1L), any(DocumentRequest.class)))
                .thenThrow(new ConflictException("Vehicle with id 1 already has an active OC_POLICY document"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Vehicle with id 1 already has an active OC_POLICY document"));
    }

    @Test
    void createReturnsConflictProblemWhenDatabaseConstraintIsViolated() throws Exception {
        when(documentService.create(eq(1L), any(DocumentRequest.class)))
                .thenThrow(new DataIntegrityViolationException("fk_document_vehicle"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Request conflicts with existing data"));
    }

    @Test
    void changeStatusReturnsUpdatedDocument() throws Exception {
        when(documentService.changeStatus(eq(1L), eq(5L), any(DocumentStatusRequest.class)))
                .thenReturn(response(DocumentStatus.VERIFIED));

        mockMvc.perform(put(URL + "/5/status").contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void changeStatusReturnsNotFoundProblemWhenVehicleDoesNotExist() throws Exception {
        when(documentService.changeStatus(eq(99L), eq(5L), any(DocumentStatusRequest.class)))
                .thenThrow(new NotFoundException("Vehicle with id 99 not found"));

        mockMvc.perform(put("/api/vehicles/99/documents/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Vehicle with id 99 not found"));
    }

    @Test
    void changeStatusReturnsNotFoundProblemWhenDocumentDoesNotExist() throws Exception {
        when(documentService.changeStatus(eq(1L), eq(99L), any(DocumentStatusRequest.class)))
                .thenThrow(new NotFoundException("Document with id 99 not found"));

        mockMvc.perform(put(URL + "/99/status").contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Document with id 99 not found"));
    }

    @Test
    void changeStatusReturnsBadRequestProblemWithFieldErrorWhenStatusIsMissing() throws Exception {
        mockMvc.perform(put(URL + "/5/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
        verifyNoInteractions(documentService);
    }

    @Test
    void changeStatusReturnsBadRequestProblemWithFieldErrorWhenStatusIsUnknown() throws Exception {
        String body = VERIFY_BODY.replace("VERIFIED", "APPROVED");

        mockMvc.perform(put(URL + "/5/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of: PENDING, VERIFIED, REJECTED"));
        verifyNoInteractions(documentService);
    }

    @Test
    void changeStatusReturnsConflictProblemWhenTransitionIsNotAllowed() throws Exception {
        when(documentService.changeStatus(eq(1L), eq(5L), any(DocumentStatusRequest.class)))
                .thenThrow(new ConflictException("Document with id 5 cannot change status from REJECTED to VERIFIED"));

        mockMvc.perform(put(URL + "/5/status").contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail")
                        .value("Document with id 5 cannot change status from REJECTED to VERIFIED"));
    }

    private static String invalidBody() {
        return """
                {
                  "number": "",
                  "issueDate": "2999-01-01"
                }
                """;
    }

    private static DocumentResponse response(DocumentStatus status) {
        return new DocumentResponse(5L, 1L, DocumentType.OC_POLICY, NUMBER,
                LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 19), status);
    }
}
