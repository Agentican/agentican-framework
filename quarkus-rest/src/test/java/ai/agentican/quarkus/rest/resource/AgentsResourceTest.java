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

        var name = "test-agent-" + System.nanoTime();

        try {

            given()
                    .contentType("application/json")
                    .body("""
                            {"name":"%s","role":"Reviews data"}
                            """.formatted(name))
                    .when().post("/agentican/agents")
                    .then()
                    .statusCode(201)
                    .body("name", equalTo(name))
                    .body("declaredInConfig", is(false));

            given()
                    .when().get("/agentican/agents/" + name)
                    .then()
                    .statusCode(200)
                    .body("role", equalTo("Reviews data"));

            given()
                    .contentType("application/json")
                    .body("""
                            {"name":"%s","role":"Reviews quarterly data"}
                            """.formatted(name))
                    .when().put("/agentican/agents/" + name)
                    .then()
                    .statusCode(200)
                    .body("role", equalTo("Reviews quarterly data"));
        }
        finally {

            given().when().delete("/agentican/agents/" + name).then().statusCode(204);
        }

        given()
                .when().get("/agentican/agents/" + name)
                .then()
                .statusCode(404);
    }

    @Test
    void createDuplicateNameReturns409() {

        var name = "dup-agent-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {"name":"%s","role":"role"}
                            """.formatted(name))
                    .when().post("/agentican/agents")
                    .then().statusCode(201);

            given().contentType("application/json")
                    .body("""
                            {"name":"%s","role":"role"}
                            """.formatted(name))
                    .when().post("/agentican/agents")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("already_exists"));
        }
        finally {

            given().when().delete("/agentican/agents/" + name).then().statusCode(204);
        }
    }

    @Test
    void createMissingFieldReturns400() {

        given().contentType("application/json")
                .body("""
                        {"name":"","role":"role"}
                        """)
                .when().post("/agentican/agents")
                .then()
                .statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    void deleteUnknownReturns404() {

        given().when().delete("/agentican/agents/nope-" + System.nanoTime())
                .then()
                .statusCode(404);
    }
}
