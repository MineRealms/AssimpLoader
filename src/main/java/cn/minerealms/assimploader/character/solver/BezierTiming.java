package cn.minerealms.assimploader.character.solver;

/** Unit cubic Bezier time remapping for independent position axes and rotation timing. */
public record BezierTiming(double x1, double y1, double x2, double y2) {
    public static final BezierTiming LINEAR = new BezierTiming(0, 0, 1, 1);
    public BezierTiming {
        for (double v : new double[]{x1,y1,x2,y2})
            if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Bezier controls must be in [0,1]");
    }
    public double sample(double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("Non-finite curve time");
        if (time <= 0) return 0;
        if (time >= 1) return 1;
        double low=0, high=1;
        // Bisection handles stationary endpoint derivatives without Newton divergence.
        for (int i=0;i<40;i++) {
            double t=(low+high)*.5;
            if (cubic(t,x1,x2)<time) low=t; else high=t;
        }
        return cubic((low+high)*.5,y1,y2);
    }
    private static double cubic(double t,double a,double b) {
        double s=1-t; return 3*s*s*t*a+3*s*t*t*b+t*t*t;
    }
}
