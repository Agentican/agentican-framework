package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ConfigCatalogResourceTest {

    @BeforeAll
    static void allowYamlBodyEncoding() {

        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    @Test
    void exportJsonReturnsCurrentCatalog() {

        given().when().get("/agentican/config/export")
                .then()
                .statusCode(200)
                .body("agents", notNullValue())
                .body("skills", notNullValue())
                .body("workflows", notNullValue());
    }

    @Test
    void exportYamlReturnsYamlAttachment() {

        given().when().get("/agentican/config/export.yaml")
                .then()
                .statusCode(200)
                .contentType(anyOf(containsString("yaml"), containsString("YAML")))
                .header("Content-Disposition", containsString("catalog.yaml"))
                .body(containsString("agents"))
                .body(containsString("skills"))
                .body(containsString("workflows"));
    }

    @Test
    void dryRunImportReportsCountsWithoutMutating() {

        var name = "dry-analyst-" + System.nanoTime();

        var body = """
                {
                  "agents": [
                    {"name": "%s", "role": "Reviews", "llm": null}
                  ],
                  "skills": [],
                  "workflows": []
                }
                """.formatted(name);

        given().contentType("application/json").body(body).queryParam("dryRun", true)
                .when().post("/agentican/config/import")
                .then()
                .statusCode(200)
                .body("dryRun", is(true))
                .body("agents.created", is(1))
                .body("errors", hasSize(0));

        given().when().get("/agentican/agents/" + name).then().statusCode(404);
    }

    @Test
    void importRoundTripCreatesThenUpdates() {

        var name = "imp-analyst-" + System.nanoTime();

        var createBody = """
                {
                  "agents": [
                    {"name": "%s", "role": "Initial role", "llm": null}
                  ],
                  "skills": [],
                  "workflows": []
                }
                """.formatted(name);

        try {

            given().contentType("application/json").body(createBody)
                    .when().post("/agentican/config/import")
                    .then()
                    .statusCode(200)
                    .body("dryRun", is(false))
                    .body("agents.created", is(1))
                    .body("agents.updated", is(0));

            var updateBody = """
                    {
                      "agents": [
                        {"name": "%s", "role": "Updated role", "llm": null}
                      ],
                      "skills": [],
                      "workflows": []
                    }
                    """.formatted(name);

            given().contentType("application/json").body(updateBody)
                    .when().post("/agentican/config/import")
                    .then()
                    .statusCode(200)
                    .body("agents.created", is(0))
                    .body("agents.updated", is(1));

            given().when().get("/agentican/agents/" + name)
                    .then()
                    .statusCode(200)
                    .body("role", equalTo("Updated role"));
        }
        finally {

            given().when().delete("/agentican/agents/" + name).then()
                    .statusCode(anyOf(is(204), is(404)));
        }
    }

    @Test
    void importInvalidPlanRecordsError() {

        var name = "bad-imp-definition-" + System.nanoTime();

        var body = """
                {
                  "agents": [],
                  "skills": [],
                  "workflows": [
                    {
                      "name": "%s",
                      "outputStep": "ghost",
                      "params": [],
                      "steps": [
                        {
                          "type": "agent",
                          "name": "ghost",
                          "agentName": "nonexistent-agent",
                          "instructions": "work",
                          "dependencies": [],
                          "hitl": false,
                          "skills": [],
                          "tools": []
                        }
                      ]
                    }
                  ]
                }
                """.formatted(name);

        given().contentType("application/json").body(body)
                .when().post("/agentican/config/import")
                .then()
                .statusCode(200)
                .body("workflows.skipped", is(1))
                .body("workflows.created", is(0))
                .body("errors", hasSize(1));
    }

    @Test
    void importYamlBodyParses() {

        var name = "yaml-analyst-" + System.nanoTime();

        var body = """
                agents:
                  - name: %s
                    role: Came in via YAML
                    llm: null
                skills: []
                workflows: []
                """.formatted(name);

        try {

            given().contentType("application/yaml").body(body)
                    .when().post("/agentican/config/import")
                    .then()
                    .statusCode(200)
                    .body("agents.created", is(1))
                    .body("errors", hasSize(0));
        }
        finally {

            given().when().delete("/agentican/agents/" + name).then()
                    .statusCode(anyOf(is(204), is(404)));
        }
    }

    @Test
    void importEmptyBodyReturns400() {

        given().contentType("application/json").body("")
                .when().post("/agentican/config/import")
                .then()
                .statusCode(400);
    }
}
