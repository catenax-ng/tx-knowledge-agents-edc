// Copyright (c) 2022,2024 Contributors to the Eclipse Foundation
//
// See the NOTICE file(s) distributed with this work for additional
// information regarding copyright ownership.
//
// This program and the accompanying materials are made available under the
// terms of the Apache License, Version 2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations
// under the License.
//
// SPDX-License-Identifier: Apache-2.0
package org.eclipse.tractusx.agents.edc.http;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.tractusx.agents.edc.rdf.RdfStore;
import org.eclipse.tractusx.agents.edc.service.InMemorySkillStore;
import org.eclipse.tractusx.agents.edc.sparql.DataspaceServiceExecutor;
import org.eclipse.tractusx.agents.edc.sparql.SparqlQueryProcessor;
import okhttp3.*;
import org.apache.jena.sparql.service.ServiceExecutorRegistry;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.agents.edc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.jena.fuseki.Fuseki;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletContext;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;

/**
 * Tests the agent controller
 */
public class TestAgentController extends RestControllerTestBase {
    
    ConsoleMonitor monitor=new ConsoleMonitor();
    TestConfig config=new TestConfig();
    AgentConfig agentConfig=new AgentConfig(monitor,config);
    ServiceExecutorRegistry serviceExecutorReg=new ServiceExecutorRegistry();
    OkHttpClient client=new OkHttpClient();
    AgreementController mockController = new MockAgreementController("test",port);
    ExecutorService threadedExecutor= Executors.newSingleThreadExecutor();
    TypeManager typeManager = new JacksonTypeManager();
    DataspaceServiceExecutor exec=new DataspaceServiceExecutor(monitor,mockController,agentConfig,client,threadedExecutor,typeManager);
    RdfStore store = new RdfStore(agentConfig,monitor);


    SparqlQueryProcessor processor=new SparqlQueryProcessor(serviceExecutorReg,monitor,agentConfig,store, typeManager);
    InMemorySkillStore skillStore=new InMemorySkillStore(agentConfig);

    DelegationServiceImpl delegationService=new DelegationServiceImpl(mockController,monitor,client,typeManager,agentConfig);
    AgentController agentController=new AgentController(monitor,mockController,agentConfig,processor,skillStore,delegationService);

    AutoCloseable mocks=null;

    TestController testController=new TestController();

    @BeforeEach
    public void setUp()  {
        mocks=MockitoAnnotations.openMocks(this);
        //serviceExecutorReg.add(exec);
        serviceExecutorReg.addBulkLink(exec);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(mocks!=null) {
            mocks.close();
            mocks=null;
            serviceExecutorReg.remove(exec);
            serviceExecutorReg.removeBulkLink(exec);
        }
    }

    @Override
    protected Object controller() {
        return testController;
    }

    @Mock
    HttpServletRequest request;
 
    @Mock
    HttpServletResponse response;

    @Mock
    ServletContext context;

    @Mock
    HttpHeaders headers;

    @Mock
    UriInfo uriInfo;

    ObjectMapper mapper=new ObjectMapper();

    /**
     * execution helper
     * @param method http method
     * @param query optional query
     * @param asset optional asset name
     * @param accepts determines return representation
     * @param params additional parameters
     * @return response body as string
     */
    protected String testExecute(String method, String query, String asset, String accepts, List<Map.Entry<String,String>> params) throws IOException {
        Map<String,String[]> fparams=new HashMap<>();
        MultivaluedMap<String,String> mparams=new MultivaluedHashMap<>();
        StringBuilder queryString=new StringBuilder();
        boolean isFirst=true;
        for(Map.Entry<String,String> param : params) {
            if(isFirst) {
                isFirst=false;
            } else {
                queryString.append("&");
            }
            queryString.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8));
            queryString.append("=");
            queryString.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
            mparams.add(param.getKey(),param.getValue());
            if(fparams.containsKey(param.getKey())) {
                String[] oarray=fparams.get(param.getKey());
                String[] narray=new String[oarray.length+1];
                System.arraycopy(oarray,0,narray,0,oarray.length);
                narray[oarray.length]=param.getValue();
                fparams.put(param.getKey(),narray);
            } else {
                String[] narray=new String[] { param.getValue() };
                fparams.put(param.getKey(),narray);
            }
        }
        when(request.getQueryString()).thenReturn(queryString.toString());
        when(request.getMethod()).thenReturn(method);
        if(query!=null) {
            fparams.put("query",new String[] { query });
            when(request.getParameter("query")).thenReturn(query);
        }
        if(asset!=null) {
            fparams.put("asset",new String[] { asset });
            when(request.getParameter("asset")).thenReturn(asset);
        }
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/sparql"));
        when(request.getParameterMap()).thenReturn(fparams);
        when(request.getServletContext()).thenReturn(context);
        when(headers.getHeaderString("Accept")).thenReturn(accepts);
        when(request.getHeaders("Accept")).thenReturn(Collections.enumeration(List.of(accepts)));
        when(context.getAttribute(Fuseki.attrVerbose)).thenReturn(false);
        when(context.getAttribute(Fuseki.attrOperationRegistry)).thenReturn(processor.getOperationRegistry());
        when(context.getAttribute(Fuseki.attrNameRegistry)).thenReturn(processor.getDataAccessPointRegistry());
        when(context.getAttribute(Fuseki.attrNameRegistry)).thenReturn(processor.getDataAccessPointRegistry());
        ByteArrayOutputStream responseStream=new ByteArrayOutputStream();
        MockServletOutputStream mos=new MockServletOutputStream(responseStream);
        when(response.getOutputStream()).thenReturn(mos);
        when(uriInfo.getQueryParameters()).thenReturn(mparams);
        agentController.getQuery(asset,headers,request,response,uriInfo);
        return responseStream.toString();
    }

    /**
     * test canonical call with fixed binding
     * @throws IOException in case of an error
     */
    @Test
    public void testFixedQuery() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?what WHERE { VALUES (?what) { (\"42\"^^xsd:int)} }";
        String result=testExecute("GET",query,null,"*/*",new ArrayList<>());
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(1,bindings.size(),"Correct number of result bindings.");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("42",whatBinding0.get("value").asText(),"Correct binding");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQuerySingle() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?what WHERE { VALUES (?what) { (\"@input\"^^xsd:int)} }";
        String result=testExecute("GET",query,null,"*/*",List.of(new AbstractMap.SimpleEntry<>("input","84")));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(1,bindings.size(),"Correct number of result bindings.");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("84",whatBinding0.get("value").asText(),"Correct binding");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQueryMultiSingleResult() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?what WHERE { VALUES ?what { \"@input\"^^xsd:int } }";
        String result=testExecute("GET",query,null,"*/*",List.of(new AbstractMap.SimpleEntry<>("input","42"),new AbstractMap.SimpleEntry<>("input","84")));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(1,bindings.size(),"Correct number of result bindings.");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("42",whatBinding0.get("value").asText(),"Correct binding");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQueryMultiMultiResult() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?what WHERE { VALUES (?what) { (\"@input\"^^xsd:int)} }";
        String result=testExecute("GET",query,null,"*/*",List.of(new AbstractMap.SimpleEntry<>("input","42"),new AbstractMap.SimpleEntry<>("input","84")));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(2,bindings.size(),"Correct number of result bindings.");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("42",whatBinding0.get("value").asText(),"Correct binding");
        JsonNode whatBinding1=bindings.get(1).get("what");
        assertEquals("84",whatBinding1.get("value").asText(),"Correct binding");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQueryTupleResult() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?so ?what WHERE { VALUES (?so ?what) { (\"@input1\"^^xsd:int \"@input2\"^^xsd:int)} }";
        String result=testExecute("GET",query,null,"*/*",
            List.of(new AbstractMap.SimpleEntry<>("input1","42"),
                    new AbstractMap.SimpleEntry<>("input2","84"),
                    new AbstractMap.SimpleEntry<>("input1","43"),
                    new AbstractMap.SimpleEntry<>("input2","85")
                    ));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(4,bindings.size(),"Correct number of result bindings.");
        JsonNode soBinding0=bindings.get(0).get("so");
        assertEquals("42",soBinding0.get("value").asText(),"Correct binding 0");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("84",whatBinding0.get("value").asText(),"Correct binding 0");
        JsonNode soBinding1=bindings.get(1).get("so");
        assertEquals("43",soBinding1.get("value").asText(),"Correct binding 1");
        JsonNode whatBinding1=bindings.get(1).get("what");
        assertEquals("84",whatBinding1.get("value").asText(),"Correct binding 1");
        JsonNode soBinding2=bindings.get(2).get("so");
        assertEquals("42",soBinding2.get("value").asText(),"Correct binding 2");
        JsonNode whatBinding2=bindings.get(2).get("what");
        assertEquals("85",whatBinding2.get("value").asText(),"Correct binding 2");
        JsonNode soBinding3=bindings.get(3).get("so");
        assertEquals("43",soBinding3.get("value").asText(),"Correct binding 3");
        JsonNode whatBinding3=bindings.get(3).get("what");
        assertEquals("85",whatBinding3.get("value").asText(),"Correct binding 3");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQueryTupleResultOrderIrrelevant() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?so ?what WHERE { VALUES (?so ?what) { (\"@input1\"^^xsd:int \"@input2\"^^xsd:int)} }";
        String result=testExecute("GET",query,null,"*/*",
            List.of(new AbstractMap.SimpleEntry<>("input2","84"),
                    new AbstractMap.SimpleEntry<>("input2","85"),
                    new AbstractMap.SimpleEntry<>("input1","42"),
                    new AbstractMap.SimpleEntry<>("input1","43")
                    ));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(4,bindings.size(),"Correct number of result bindings.");
        JsonNode soBinding0=bindings.get(0).get("so");
        assertEquals("42",soBinding0.get("value").asText(),"Correct binding 0");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("84",whatBinding0.get("value").asText(),"Correct binding 0");
        JsonNode soBinding1=bindings.get(1).get("so");
        assertEquals("43",soBinding1.get("value").asText(),"Correct binding 1");
        JsonNode whatBinding1=bindings.get(1).get("what");
        assertEquals("84",whatBinding1.get("value").asText(),"Correct binding 1");
        JsonNode soBinding2=bindings.get(2).get("so");
        assertEquals("42",soBinding2.get("value").asText(),"Correct binding 2");
        JsonNode whatBinding2=bindings.get(2).get("what");
        assertEquals("85",whatBinding2.get("value").asText(),"Correct binding 2");
        JsonNode soBinding3=bindings.get(3).get("so");
        assertEquals("43",soBinding3.get("value").asText(),"Correct binding 3");
        JsonNode whatBinding3=bindings.get(3).get("what");
        assertEquals("85",whatBinding3.get("value").asText(),"Correct binding 3");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQueryTupleResultSpecial() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?so ?what ?now WHERE { VALUES (?so ?what) { (\"@input1\"^^xsd:int \"@input2\"^^xsd:int)} VALUES (?now) { (\"@input3\"^^xsd:int)} }";
        String result=testExecute("GET",query,null,"*/*",
            List.of(new AbstractMap.SimpleEntry<>("(input2","84"),
                    new AbstractMap.SimpleEntry<>("input1","42)"),
                    new AbstractMap.SimpleEntry<>("(input2","85"),
                    new AbstractMap.SimpleEntry<>("input1","43)"),
                    new AbstractMap.SimpleEntry<>("input3","21")
                    ));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(2,bindings.size(),"Correct number of result bindings.");
        JsonNode soBinding0=bindings.get(0).get("so");
        assertEquals("42",soBinding0.get("value").asText(),"Correct binding 0");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("84",whatBinding0.get("value").asText(),"Correct binding 0");
        JsonNode nowBinding0=bindings.get(0).get("now");
        assertEquals("21",nowBinding0.get("value").asText(),"Correct binding 0");
        JsonNode soBinding1=bindings.get(1).get("so");
        assertEquals("43",soBinding1.get("value").asText(),"Correct binding 1");
        JsonNode whatBinding1=bindings.get(1).get("what");
        assertEquals("85",whatBinding1.get("value").asText(),"Correct binding 1");
        JsonNode nowBinding1=bindings.get(1).get("now");
        assertEquals("21",nowBinding1.get("value").asText(),"Correct binding 1");
    }

    /**
     * test canonical call with replacement binding which should not confuse the filtering
     * @throws IOException in case of an error
     */
    @Test
    public void testParameterizedQueryFilterContains() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?so ?what WHERE { VALUES(?so ?what) {(\"@input1\"^^xsd:string \"@input2\"^^xsd:string)} FILTER CONTAINS(?so,?what) }";
        String result=testExecute("GET",query,null,"*/*",
                List.of(new AbstractMap.SimpleEntry<>("(input2","BAR"),
                        new AbstractMap.SimpleEntry<>("input1","FOOBAR)"),
                        new AbstractMap.SimpleEntry<>("(input2","BLUB"),
                        new AbstractMap.SimpleEntry<>("input1","NOOB)")
                ));
        JsonNode root=mapper.readTree(result);
        ArrayNode bindings=(ArrayNode) root.get("results").get("bindings");
        assertEquals(1,bindings.size(),"Correct number of result bindings.");
        JsonNode soBinding0=bindings.get(0).get("so");
        assertEquals("FOOBAR",soBinding0.get("value").asText(),"Correct binding 0");
        JsonNode whatBinding0=bindings.get(0).get("what");
        assertEquals("BAR",whatBinding0.get("value").asText(),"Correct binding 0");
    }

    /**
     * test canonical call with simple replacement binding
     * @throws IOException in case of an error
     */
    @Test
    @Tag("online")
    public void testParameterizedSkill() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?what WHERE { VALUES (?what) { (\"@input\"^^xsd:int)} }";
        String asset="urn:cx:Skill:cx:Test";
        try (var response=agentController.postSkill(query,asset,null,null,null,null,
                SkillDistribution.ALL,false,null, null, null)) {
            assertEquals(200,response.getStatus(),"post skill successful");
            String result = testExecute("GET", null, asset, "*/*", List.of(new AbstractMap.SimpleEntry<>("input", "84")));
            JsonNode root = mapper.readTree(result);
            JsonNode whatBinding0 = root.get("results").get("bindings").get(0).get("what");
            assertEquals("84", whatBinding0.get("value").asText(), "Correct binding");
        }
    }

    /**
     * test federation call - will only work with a local oem provider running
     * @throws IOException in case of an error
     */
    @Test
    @Tag("online")
    public void testRemotingSkill() throws IOException {
        String query="PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?subject WHERE { SERVICE<http://localhost:8898/match> { VALUES (?subject) { (<@input>)} } }";
        String asset="urn:cx:Skill:cx:Test";
        try(var response=agentController.postSkill(query,asset,null,null,null,null,SkillDistribution.ALL,
                false,null, null, null)) {
            assertEquals(200,response.getStatus(),"post skill successful");
            String result = testExecute("GET", null, asset, "*/*", List.of(new AbstractMap.SimpleEntry<>("input", "urn:cx:AnonymousSerializedPart#GB4711")));
            JsonNode root = mapper.readTree(result);
            JsonNode whatBinding0 = root.get("results").get("bindings").get(0).get("subject");
            assertEquals("urn:cx:AnonymousSerializedPart#GB4711", whatBinding0.get("value").asText(), "Correct binding");
        }
    }

    /**
     * test invocation of a remote skill
     * @throws IOException in case of an error
     */
    @Test
    public void testRemoteSkill() throws IOException {
        String remoteSkill=String.format("http://localhost:%d/test/test#SkillAsset",port);
        String result=testExecute("GET",null,remoteSkill,"application/sparql-results+json",List.of(new AbstractMap.SimpleEntry<>("input","84")));
        JsonNode root=mapper.readTree(result);
        JsonNode bindings=root.get("results").get("bindings");
        assertEquals(1,bindings.size(),"Correct number of result bindings.");
    }

}
