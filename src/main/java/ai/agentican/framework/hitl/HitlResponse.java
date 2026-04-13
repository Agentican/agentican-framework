package ai.agentican.framework.hitl;

public record HitlResponse(
        boolean approved,
        String feedback) {

    public static HitlResponse approve() {

        return new HitlResponse(true, null);
    }

    public static HitlResponse approve(String feedback) {

        return new HitlResponse(true, feedback);
    }

    public static HitlResponse reject(String feedback) {

        return new HitlResponse(false, feedback);
    }
}
