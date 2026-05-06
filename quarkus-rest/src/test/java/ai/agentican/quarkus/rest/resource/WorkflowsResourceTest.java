package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class WorkflowsResourceTest {

    @Test
    void getUnknownReturns404() {

        given().when().get("/agentican/plans/nope-" + System.nanoTime())
                .then().statusCode(404);
    }

    @Test
    void createUpdateDeleteRoundTrip() {

        var name = "definition-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {
                              "definition": {
                                "name": "%s",
                                "description": "A test definition",
                                "outputStep": "research",
                                "params": [],
                                "steps": [
                                  {
                                    "type": "agent",
                                    "name": "research",
                                    "agentName": "researcher",
                                    "instructions": "Research the topic",
                                    "dependencies": [],
                                    "hitl": false,
                                    "skills": [],
                                    "tools": []
                                  }
                                ]
                              }
                            }
                            """.formatted(name))
                    .when().post("/agentican/plans")
                    .then()
                    .statusCode(201)
                    .body("definition.name", equalTo(name))
                    .body("definition.outputStep", equalTo("research"));

            given().when().get("/agentican/plans/" + name)
                    .then()
                    .statusCode(200)
                    .body("definition.description", equalTo("A test definition"));

            given().contentType("application/json")
                    .body("""
                            {
                              "definition": {
                                "name": "%s",
                                "description": "An updated description",
                                "outputStep": "research",
                                "params": [],
                                "steps": [
                                  {
                                    "type": "agent",
                                    "name": "research",
                                    "agentName": "researcher",
                                    "instructions": "Research the topic in depth",
                                    "dependencies": [],
                                    "hitl": false,
                                    "skills": [],
                                    "tools": []
                                  }
                                ]
                              }
                            }
                            """.formatted(name))
                    .when().put("/agentican/plans/" + name)
                    .then()
                    .statusCode(200)
                    .body("definition.description", equalTo("An updated description"));
        }
        finally {

            given().when().delete("/agentican/plans/" + name).then().statusCode(204);
        }

        given().when().get("/agentican/plans/" + name).then().statusCode(404);
    }

    @Test
    void createWithUnknownAgentReturns409Validation() {

        var name = "invalid-definition-" + System.nanoTime();

        given().contentType("application/json")
                .body("""
                        {
                          "definition": {
                            "name": "%s",
                            "description": "References a ghost",
                            "outputStep": "ghost-step",
                            "params": [],
                            "steps": [
                              {
                                "type": "agent",
                                "name": "ghost-step",
                                "agentName": "nonexistent-agent",
                                "instructions": "do something",
                                "dependencies": [],
                                "hitl": false,
                                "skills": [],
                                "tools": []
                              }
                            ]
                          }
                        }
                        """.formatted(name))
                .when().post("/agentican/plans")
                .then()
                .statusCode(409)
                .body("code", equalTo("invalid_plan"))
                .body("referring", notNullValue());
    }

    @Test
    void createDuplicateNameReturns409() {

        var name = "dup-definition-" + System.nanoTime();

        var body = """
                {
                  "definition": {
                    "name": "%s",
                    "description": "first",
                    "outputStep": "research",
                    "params": [],
                    "steps": [
                      {
                        "type": "agent",
                        "name": "research",
                        "agentName": "researcher",
                        "instructions": "x",
                        "dependencies": [],
                        "hitl": false,
                        "skills": [],
                        "tools": []
                      }
                    ]
                  }
                }
                """.formatted(name);

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

            given().when().delete("/agentican/plans/" + name).then().statusCode(204);
        }
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

        var name = "yaml-definition-" + System.nanoTime();

        var body = """
                definition:
                  name: %s
                  description: A YAML-authored definition
                  outputStep: research
                  params: []
                  steps:
                    - type: agent
                      name: research
                      agentName: researcher
                      instructions: Do research
                      dependencies: []
                      hitl: false
                      skills: []
                      tools: []
                """.formatted(name);

        try {

            given().contentType("application/yaml").body(body)
                    .when().post("/agentican/plans")
                    .then()
                    .statusCode(201)
                    .body("definition.name", equalTo(name))
                    .body("definition.description", equalTo("A YAML-authored definition"));

            given().when().get("/agentican/plans/" + name)
                    .then().statusCode(200);
        }
        finally {

            given().when().delete("/agentican/plans/" + name).then().statusCode(204);
        }
    }
}
