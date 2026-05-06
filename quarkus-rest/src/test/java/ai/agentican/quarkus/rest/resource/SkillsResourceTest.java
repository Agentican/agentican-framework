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

        var name = "test-skill-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {"name":"%s","instructions":"Cross-check every claim."}
                            """.formatted(name))
                    .when().post("/agentican/skills")
                    .then()
                    .statusCode(201)
                    .body("name", equalTo(name))
                    .body("instructions", equalTo("Cross-check every claim."))
                    .body("declaredInConfig", is(false));

            given().when().get("/agentican/skills/" + name)
                    .then()
                    .statusCode(200)
                    .body("name", equalTo(name));

            given().contentType("application/json")
                    .body("""
                            {"name":"%s","instructions":"Verify against three primary sources."}
                            """.formatted(name))
                    .when().put("/agentican/skills/" + name)
                    .then()
                    .statusCode(200)
                    .body("instructions", equalTo("Verify against three primary sources."));
        }
        finally {

            given().when().delete("/agentican/skills/" + name).then().statusCode(204);
        }

        given().when().get("/agentican/skills/" + name).then().statusCode(404);
    }

    @Test
    void createDuplicateNameReturns409() {

        var name = "dup-skill-" + System.nanoTime();

        try {

            given().contentType("application/json")
                    .body("""
                            {"name":"%s","instructions":"first"}
                            """.formatted(name))
                    .when().post("/agentican/skills")
                    .then().statusCode(201);

            given().contentType("application/json")
                    .body("""
                            {"name":"%s","instructions":"second"}
                            """.formatted(name))
                    .when().post("/agentican/skills")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("already_exists"));
        }
        finally {

            given().when().delete("/agentican/skills/" + name).then().statusCode(204);
        }
    }

    @Test
    void createMissingFieldReturns400() {

        given().contentType("application/json")
                .body("""
                        {"name":"Naming-%s","instructions":""}
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
