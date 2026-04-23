/**
 * Domain: Marketing
 * Tools: Notion (via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class ContentPipeline {

    static String TASK_NAME = "Publish Platform Engineering Article";
    static String PLAN_NAME = "Content Pipeline";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var pipeline = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(ArticleBrief.class)
                    .output(PublishedArticle.class)
                    .build();

            var article = pipeline.runAsync(brief()).join();

            print(article);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ContentPipeline.class.getResource("/content-pipeline.yaml")).toURI());
    }

    static ArticleBrief brief() {

        return new ArticleBrief(
                "how platform engineering reduces cognitive load",
                "engineering managers");
    }

    static void print(PublishedArticle article) {

        System.out.println(article.title() + "\n  " + article.notionUrl() + "\n\n" + article.summary() + "\n");
    }

    record ArticleBrief(String topic, String audience) {}

    record PublishedArticle(

            @JsonPropertyDescription("Final article title as published")
            String title,

            @JsonPropertyDescription("One-paragraph summary of the published article, 2-4 sentences")
            String summary,

            @JsonPropertyDescription("Notion page URL returned by notion_create_page")
            String notionUrl) {
    }
}
