package finos.traderx.accountservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import finos.traderx.accountservice.controller.AccountUserController;
import finos.traderx.accountservice.model.Account;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.repository.AccountUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cluster_c;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "people.service.url=http://people.test"})
@AutoConfigureMockMvc
class ClusterCAccountInputIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @SpyBean AccountUserRepository users;
    @Autowired AccountUserController controller;
    MockRestServiceServer people;
    Account existing;

    @BeforeEach
    void setup() {
        users.deleteAll();
        accounts.deleteAll();
        existing = new Account();
        existing.setDisplayName("Original");
        existing = accounts.save(existing);
        RestTemplate template = new RestTemplate();
        people = MockRestServiceServer.bindTo(template).build();
        ReflectionTestUtils.setField(controller, "restTemplate", template);
    }

    @Test
    void postIgnoresExistingClientId() throws Exception {
        String result = mvc.perform(post("/account/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + existing.getId() + ",\"displayName\":\"New\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotEquals(existing.getId(), mapper.readTree(result).get("id").asInt());
        assertEquals(2, accounts.count());
        assertEquals("Original", accounts.findById(existing.getId()).orElseThrow().getDisplayName());
    }

    @Test
    void putCannotCreateUnknownAccount() throws Exception {
        mvc.perform(put("/account/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":999999,\"displayName\":\"New\"}")).andExpect(status().isNotFound());
        assertEquals(1, accounts.count());
    }

    @Test
    void putUpdatesExistingAccount() throws Exception {
        mvc.perform(put("/account/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + existing.getId() + ",\"displayName\":\"Updated\"}"))
            .andExpect(status().isOk());
        assertEquals("Updated", accounts.findById(existing.getId()).orElseThrow().getDisplayName());
        assertEquals(1, accounts.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"})
    void invalidDisplayNameRejectedForBothMethods(String name) throws Exception {
        String body = "{\"id\":" + existing.getId() + ",\"displayName\":" + mapper.writeValueAsString(name) + "}";
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/account/"), put("/account/")}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().string("Invalid request"));
        }
        assertEquals("Original", accounts.findById(existing.getId()).orElseThrow().getDisplayName());
        assertEquals(1, accounts.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"accountId\":null,\"username\":\"user01\"}",
        "{\"accountId\":0,\"username\":\"user01\"}",
        "{\"accountId\":-1,\"username\":\"user01\"}",
        "{\"accountId\":1,\"username\":\"x&EmployeeId=1\"}",
        "{\"accountId\":1,\"username\":null}",
        "{\"accountId\":1,\"username\":\"\"}",
        "{\"accountId\":1,\"username\":\" \"}",
        "{\"accountId\":1,\"username\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ\"}"
    })
    void invalidMappingRejectedBeforePeopleLookup(String body) throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/accountuser/"), put("/accountuser/")}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().string("Invalid request"));
        }
        assertEquals(0, users.count());
        people.verify();
    }

    @Test
    void validPostUsesExactPeopleUri() throws Exception {
        foundPerson();
        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(mapping()))
            .andExpect(status().isOk());
        assertEquals(1, users.count());
        people.verify();
    }

    @Test
    void duplicatePostConflictsWithoutChangingMapping() throws Exception {
        saveMapping();
        clearInvocations(users);
        foundPerson();
        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(mapping()))
            .andExpect(status().isConflict()).andExpect(content().string("Account user already exists"));
        assertEquals(1, users.count());
        verify(users, never()).save(any(AccountUser.class));
        people.verify();
    }

    @Test
    void postRequiresExistingAccount() throws Exception {
        foundPerson();
        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":999999,\"username\":\"user01\"}"))
            .andExpect(status().isNotFound());
        assertEquals(0, users.count());
        people.verify();
    }

    @Test
    void putRequiresExistingMapping() throws Exception {
        foundPerson();
        mvc.perform(put("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(mapping()))
            .andExpect(status().isNotFound());
        assertEquals(0, users.count());
        people.verify();
    }

    @Test
    void putRejectsMissingPersonWithoutSaving() throws Exception {
        saveMapping();
        clearInvocations(users);
        people.expect(requestTo("http://people.test/People/GetPerson?LogonId=user01"))
            .andRespond(withResourceNotFound());
        mvc.perform(put("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(mapping()))
            .andExpect(status().isNotFound());
        assertEquals(1, users.count());
        verify(users, never()).save(any(AccountUser.class));
        people.verify();
    }

    @Test
    void validPutValidatesPerson() throws Exception {
        saveMapping();
        foundPerson();
        mvc.perform(put("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(mapping()))
            .andExpect(status().isOk());
        assertEquals(1, users.count());
        people.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/account/", "/accountuser/"})
    void malformedAndEmptyBodiesReturnGeneric400(String path) throws Exception {
        for (String body : new String[]{"{", ""}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().string("Invalid request"));
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().string("Invalid request"));
        }
    }

    private void foundPerson() {
        people.expect(requestTo("http://people.test/People/GetPerson?LogonId=user01"))
            .andRespond(withSuccess("{\"logonId\":\"user01\"}", MediaType.APPLICATION_JSON));
    }

    private String mapping() {
        return "{\"accountId\":" + existing.getId() + ",\"username\":\"user01\"}";
    }

    private void saveMapping() {
        AccountUser user = new AccountUser();
        user.setAccountId(existing.getId());
        user.setUsername("user01");
        users.save(user);
    }
}
