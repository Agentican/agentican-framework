package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AgentsResourceTest {

    @Test
    void listReturnsConfiguredAgents() {

        given()
                .when().get("/agentican/agents")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("name", hasItem("researcher"));
    }

    @Test
    void getByNameReturnsAgent() {

        given()
                .when().get("/agentican/agents/researcher")
                .then()
                .statusCode(200)
                .body("name", equalTo("researcher"))
                .body("role", equalTo("Expert at finding information"))
                .body("declaredInConfig", is(true));
    }

    @Test
    void getByNameReturns404ForUnknownAgent() {

        given()
                .when().get("/agentican/agents/nonexistent")
                .then()
                .statusCode(404);
    }

    @Test
    void createUpdateDeleteRoundTrip() {

        var externalId = "test-agent-" + System.nanoTime();

        try {

            given()
                    .contentType("application/json")
                    .body("""
                            {"externalId":"%s","name":"analyst","role":"Reviews data"}
                            """.formatted(externalId))
                    .when().post("/agentican/agents")
                    .then()
                    .statusCode(201)
                    .body("externalId", equalTo(externalId))
                    .body("name", equalTo("analyst"))
                    .body("declaredInConfig", is(false));

            given()
                    .when().get("/agentican/agents/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("role", equalTo("Reviews data"));

            given()
                    .contentType("application/json")
                    .body("""
                            {"name":"analyst","role":"Reviews quarterly data"}
                            """)
                    .when().put("/agentican/agents/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("role", equalTo("Reviews quarterly data"));
        }
        finally {

            given().when().delete("/agentican/agents/" + externalId).then().statusCode(204);
        }

        given()
                .when().get("/agentican/agents/" + externalId)
                .then()
                .statusCode(404);
    }

    @Test
    void createDuplicateExternalIdReturns409() {

        var externalId = "dup-agent-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {"externalId":"%s","name":"first","role":"role"}
                            """.formatted(externalId))
                    .when().post("/agentican/agents")
                    .then().statusCode(201);

            given().contentType("application/json")
                    .body("""
                            {"externalId":"%s","name":"second","role":"role"}
                            """.formatted(externalId))
                    .when().post("/agentican/agents")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("already_exists"));
        }
        finally {

            given().when().delete("/agentican/agents/" + externalId).then().statusCode(204);
        }
    }

    @Test
    void createCollidingWithPropertyDeclaredReturns409() {

        given().contentType("application/json")
                .body("""
                        {"externalId":"researcher","name":"dup","role":"dup"}
                        """)
                .when().post("/agentican/agents")
                .then()
                .statusCode(409)
                .body("code", equalTo("property_declared"));
    }

    @Test
    void createMissingFieldReturns400() {

        given().contentType("application/json")
                .body("""
                        {"externalId":"","name":"missing","role":"role"}
                        """)
                .when().post("/agentican/agents")
                .then()
                .statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    void updatePropertyDeclaredReturns409() {

        given().contentType("application/json")
                .body("""
                        {"name":"researcher","role":"changed"}
                        """)
                .when().put("/agentican/agents/researcher")
                .then()
                .statusCode(409)
                .body("code", equalTo("property_declared"));
    }

    @Test
    void deletePropertyDeclaredReturns409() {

        given().when().delete("/agentican/agents/researcher")
                .then()
                .statusCode(409)
                .body("code", equalTo("property_declared"));
    }

    @Test
    void deleteUnknownReturns404() {

        given().when().delete("/agentican/agents/nope-" + System.nanoTime())
                .then()
                .statusCode(404);
    }
}
