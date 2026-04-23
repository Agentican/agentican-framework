package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.hitl.HitlResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class CliHitlNotifier implements HitlNotifier {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Scanner IN = new Scanner(System.in);

    @Override
    public synchronized void onCheckpoint(HitlManager manager, HitlCheckpoint checkpoint) {

        var response = switch (checkpoint.type()) {

            case QUESTION       -> promptQuestion(checkpoint);
            case STEP_OUTPUT    -> promptStepApproval(checkpoint);
            case TOOL_CALL      -> promptToolApproval(checkpoint);
        };

        manager.respond(checkpoint.id(), response);
    }

    private static HitlResponse promptQuestion(HitlCheckpoint checkpoint) {

        var bar = "=".repeat(72);
        var thin = "-".repeat(72);

        System.out.println();
        System.out.println(bar);
        System.out.println("AGENT QUESTION");
        System.out.println(bar);
        System.out.println("Step     : " + checkpoint.stepName());
        System.out.println("Question : " + checkpoint.description());

        if (checkpoint.content() != null && !checkpoint.content().isBlank()) {

            System.out.println(thin);
            System.out.println("Context:");
            System.out.println(checkpoint.content());
        }

        System.out.println(thin);
        System.out.print("Answer: ");

        var answer = readLineOrEmpty().trim();

        System.out.println("→ answered\n");

        return HitlResponse.approve(answer.isBlank() ? "(no answer provided)" : answer);
    }

    private static HitlResponse promptStepApproval(HitlCheckpoint checkpoint) {

        printApprovalBanner("HUMAN APPROVAL REQUIRED", checkpoint);

        System.out.print("Approve? [y/N] ");
        var approved = readYes();

        if (approved) {

            System.out.println("→ approved\n");
            return HitlResponse.approve();
        }

        System.out.print("Feedback for the agent to revise: ");
        var feedback = readLineOrEmpty();

        System.out.println("→ rejected, re-running with feedback\n");

        return HitlResponse.reject(feedback.isBlank() ? "Please revise." : feedback);
    }

    private static HitlResponse promptToolApproval(HitlCheckpoint checkpoint) {

        printApprovalBanner("TOOL CALL APPROVAL", checkpoint);

        System.out.print("Approve? [y/N] ");
        var approved = readYes();

        if (approved) {

            System.out.println("→ approved\n");
            return HitlResponse.approve();
        }

        System.out.print("Reason for rejection: ");
        var feedback = readLineOrEmpty();

        System.out.println("→ rejected\n");

        return HitlResponse.reject(feedback.isBlank() ? "Rejected." : feedback);
    }

    private static void printApprovalBanner(String title, HitlCheckpoint checkpoint) {

        var bar = "=".repeat(72);
        var thin = "-".repeat(72);

        System.out.println();
        System.out.println(bar);
        System.out.println(title);
        System.out.println(bar);
        System.out.println("Step : " + checkpoint.stepName());
        System.out.println("Type : " + checkpoint.type());
        System.out.println("What : " + checkpoint.description());
        System.out.println(thin);
        System.out.println(prettyIfJson(checkpoint.content()));
        System.out.println(thin);
    }

    private static boolean readYes() {

        var line = readLineOrEmpty().trim().toLowerCase();
        return line.equals("y") || line.equals("yes");
    }

    private static String readLineOrEmpty() {

        try {
            return IN.nextLine();
        }
        catch (NoSuchElementException _) {
            return "";
        }
    }

    private static String prettyIfJson(String content) {

        if (content == null || content.isBlank()) return content;

        var trimmed = content.trim();

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return content;

        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(trimmed));
        }
        catch (Exception _) {
            return content;
        }
    }
}
