package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SkillsResourceTest {

    @Test
    void listReturnsEmpty() {

        given().when().get("/agentican/skills").then().statusCode(200);
    }

    @Test
    void getUnknownReturns404() {

        given().when().get("/agentican/skills/nope-" + System.nanoTime())
                .then().statusCode(404);
    }

    @Test
    void createUpdateDeleteRoundTrip() {

        var externalId = "test-skill-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {"externalId":"%s","name":"Deep Diligence","instructions":"Cross-check every claim."}
                            """.formatted(externalId))
                    .when().post("/agentican/skills")
                    .then()
                    .statusCode(201)
                    .body("externalId", equalTo(externalId))
                    .body("name", equalTo("Deep Diligence"))
                    .body("instructions", equalTo("Cross-check every claim."))
                    .body("declaredInConfig", is(false));

            given().when().get("/agentican/skills/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("name", equalTo("Deep Diligence"));

            given().contentType("application/json")
                    .body("""
                            {"name":"Deep Diligence","instructions":"Verify against three primary sources."}
                            """)
                    .when().put("/agentican/skills/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("instructions", equalTo("Verify against three primary sources."));
        }
        finally {

            given().when().delete("/agentican/skills/" + externalId).then().statusCode(204);
        }

        given().when().get("/agentican/skills/" + externalId).then().statusCode(404);
    }

    @Test
    void createDuplicateExternalIdReturns409() {

        var externalId = "dup-skill-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {"externalId":"%s","name":"One","instructions":"first"}
                            """.formatted(externalId))
                    .when().post("/agentican/skills")
                    .then().statusCode(201);

            given().contentType("application/json")
                    .body("""
                            {"externalId":"%s","name":"Two","instructions":"second"}
                            """.formatted(externalId))
                    .when().post("/agentican/skills")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("already_exists"));
        }
        finally {

            given().when().delete("/agentican/skills/" + externalId).then().statusCode(204);
        }
    }

    @Test
    void createMissingFieldReturns400() {

        given().contentType("application/json")
                .body("""
                        {"externalId":"no-instr-%s","name":"Naming","instructions":""}
                        """.formatted(System.nanoTime()))
                .when().post("/agentican/skills")
                .then()
                .statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    void deleteUnknownReturns404() {

        given().when().delete("/agentican/skills/nope-" + System.nanoTime())
                .then().statusCode(404);
    }
}
