package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class AgentsResourceTest {

    @Test
    void listReturnsConfiguredAgents() {

        given()
                .when().get("/agentican/agents")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("name", hasItem("researcher"));
    }

    @Test
    void getByNameReturnsAgent() {

        given()
                .when().get("/agentican/agents/researcher")
                .then()
                .statusCode(200)
                .body("name", equalTo("researcher"))
                .body("role", equalTo("Expert at finding information"));
    }

    @Test
    void getByNameReturns404ForUnknownAgent() {

        given()
                .when().get("/agentican/agents/nonexistent")
                .then()
                .statusCode(404);
    }
}
