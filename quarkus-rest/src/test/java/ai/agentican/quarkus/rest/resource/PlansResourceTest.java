package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PlansResourceTest {

    @Test
    void getUnknownReturns404() {

        given().when().get("/agentican/plans/nope-" + System.nanoTime())
                .then().statusCode(404);
    }

    @Test
    void createUpdateDeleteRoundTrip() {

        var externalId = "test-plan-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {
                              "plan": {
                                "name": "plan-%s",
                                "description": "A test plan",
                                "externalId": "%s",
                                "outputStep": "research",
                                "params": [],
                                "steps": [
                                  {
                                    "type": "agent",
                                    "name": "research",
                                    "agentId": "researcher",
                                    "instructions": "Research the topic",
                                    "dependencies": [],
                                    "hitl": false,
                                    "skills": [],
                                    "tools": []
                                  }
                                ]
                              }
                            }
                            """.formatted(externalId, externalId))
                    .when().post("/agentican/plans")
                    .then()
                    .statusCode(201)
                    .body("plan.externalId", equalTo(externalId))
                    .body("plan.outputStep", equalTo("research"));

            given().when().get("/agentican/plans/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("plan.description", equalTo("A test plan"));

            given().contentType("application/json")
                    .body("""
                            {
                              "plan": {
                                "name": "plan-%s",
                                "description": "An updated description",
                                "outputStep": "research",
                                "params": [],
                                "steps": [
                                  {
                                    "type": "agent",
                                    "name": "research",
                                    "agentId": "researcher",
                                    "instructions": "Research the topic in depth",
                                    "dependencies": [],
                                    "hitl": false,
                                    "skills": [],
                                    "tools": []
                                  }
                                ]
                              }
                            }
                            """.formatted(externalId))
                    .when().put("/agentican/plans/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("plan.description", equalTo("An updated description"));
        }
        finally {

            given().when().delete("/agentican/plans/" + externalId).then().statusCode(204);
        }

        given().when().get("/agentican/plans/" + externalId).then().statusCode(404);
    }

    @Test
    void createWithUnknownAgentReturns409Validation() {

        var externalId = "invalid-plan-" + System.nanoTime();

        given().contentType("application/json")
                .body("""
                        {
                          "plan": {
                            "name": "plan-%s",
                            "description": "References a ghost",
                            "externalId": "%s",
                            "outputStep": "ghost-step",
                            "params": [],
                            "steps": [
                              {
                                "type": "agent",
                                "name": "ghost-step",
                                "agentId": "nonexistent-agent",
                                "instructions": "do something",
                                "dependencies": [],
                                "hitl": false,
                                "skills": [],
                                "tools": []
                              }
                            ]
                          }
                        }
                        """.formatted(externalId, externalId))
                .when().post("/agentican/plans")
                .then()
                .statusCode(409)
                .body("code", equalTo("invalid_plan"))
                .body("referring", notNullValue());
    }

    @Test
    void createDuplicateExternalIdReturns409() {

        var externalId = "dup-plan-" + System.nanoTime();

        var body = """
                {
                  "plan": {
                    "name": "plan-%s",
                    "description": "first",
                    "externalId": "%s",
                    "outputStep": "research",
                    "params": [],
                    "steps": [
                      {
                        "type": "agent",
                        "name": "research",
                        "agentId": "researcher",
                        "instructions": "x",
                        "dependencies": [],
                        "hitl": false,
                        "skills": [],
                        "tools": []
                      }
                    ]
                  }
                }
                """.formatted(externalId, externalId);

        try {

            given().contentType("application/json").body(body)
                    .when().post("/agentican/plans")
                    .then().statusCode(201);

            given().contentType("application/json").body(body)
                    .when().post("/agentican/plans")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("already_exists"));
        }
        finally {

            given().when().delete("/agentican/plans/" + externalId).then().statusCode(204);
        }
    }

    @Test
    void createMissingExternalIdReturns400() {

        given().contentType("application/json")
                .body("""
                        {
                          "plan": {
                            "name": "no-ext-id",
                            "description": "missing externalId",
                            "params": [],
                            "steps": []
                          }
                        }
                        """)
                .when().post("/agentican/plans")
                .then().statusCode(400);
    }

    @Test
    void deleteUnknownReturns404() {

        given().when().delete("/agentican/plans/nope-" + System.nanoTime())
                .then().statusCode(404);
    }

    @Test
    void createAcceptsYamlBody() {

        io.restassured.RestAssured.config = io.restassured.RestAssured.config().encoderConfig(
                io.restassured.config.EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", io.restassured.http.ContentType.TEXT));

        var externalId = "yaml-plan-" + System.nanoTime();

        var body = """
                plan:
                  name: plan-%s
                  description: A YAML-authored plan
                  externalId: %s
                  outputStep: research
                  params: []
                  steps:
                    - type: agent
                      name: research
                      agentId: researcher
                      instructions: Do research
                      dependencies: []
                      hitl: false
                      skills: []
                      tools: []
                """.formatted(externalId, externalId);

        try {

            given().contentType("application/yaml").body(body)
                    .when().post("/agentican/plans")
                    .then()
                    .statusCode(201)
                    .body("plan.externalId", equalTo(externalId))
                    .body("plan.description", equalTo("A YAML-authored plan"));

            given().when().get("/agentican/plans/" + externalId)
                    .then().statusCode(200);
        }
        finally {

            given().when().delete("/agentican/plans/" + externalId).then().statusCode(204);
        }
    }
}
