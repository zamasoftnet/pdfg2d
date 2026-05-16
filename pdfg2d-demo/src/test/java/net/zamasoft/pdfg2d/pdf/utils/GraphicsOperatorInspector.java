package net.zamasoft.pdfg2d.pdf.utils;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

/**
 * A utility class to inspect PDF graphics operators.
 */
public class GraphicsOperatorInspector extends PDFGraphicsStreamEngine {

    public static class ShapeCommand {
        public final String operation;
        public final List<Float> operands;
        public final Point2D.Float currentPoint;
        public final float[] currentColor;
        public final float alpha; // Alpha value (0.0 to 1.0)

        public ShapeCommand(String operation, List<Float> operands, Point2D.Float currentPoint, float[] currentColor,
                float alpha) {
            this.operation = operation;
            this.operands = operands;
            this.currentPoint = currentPoint;
            this.currentColor = currentColor;
            this.alpha = alpha;
        }

        @Override
        public String toString() {
            return String.format("Op: %s, Args: %s, Alpha: %.2f", operation, operands, alpha);
        }
    }

    private final List<ShapeCommand> commands = new ArrayList<>();

    public GraphicsOperatorInspector(PDPage page) {
        super(page);
    }

    public void run() throws IOException {
        processPage(getPage());
    }

    public List<ShapeCommand> getCommands() {
        return commands;
    }

    // Overridden methods to capture operations

    private Point2D currentPoint;

    @Override
    public Point2D getCurrentPoint() throws IOException {
        return currentPoint;
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {
        // Record 're' or equivalent rectangle construction
        // PDFBox might call this for 're' operator: x y w h re
        // p0=bl, p1=br, p2=tr, p3=tl usually
        List<Float> args = new ArrayList<>();
        args.add((float) p0.getX());
        args.add((float) p0.getY());
        args.add((float) p1.getX() - (float) p0.getX()); // width
        args.add((float) p3.getY() - (float) p0.getY()); // height
        record("re", args);
        // re usually doesn't update current point for subsequent ops in the same way
        // m/l do,
        // but let's set it to start of rect for consistency if needed.
        this.currentPoint = p0;
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        record("Do", null);
    }

    @Override
    public void clip(int windingRule) throws IOException {
        record(windingRule == 0 ? "W" : "W*", null);
    }

    @Override
    public void moveTo(float x, float y) throws IOException {
        this.currentPoint = new Point2D.Float(x, y);
        record("m", List.of(x, y));
    }

    @Override
    public void lineTo(float x, float y) throws IOException {
        this.currentPoint = new Point2D.Float(x, y);
        record("l", List.of(x, y));
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException {
        this.currentPoint = new Point2D.Float(x3, y3);
        record("c", List.of(x1, y1, x2, y2, x3, y3));
    }

    @Override
    public void closePath() throws IOException {
        record("h", null);
    }

    @Override
    public void endPath() throws IOException {
        // 'n' operator usually
        record("n", null);
    }

    @Override
    public void strokePath() throws IOException {
        record("S", null);
    }

    @Override
    public void fillPath(int windingRule) throws IOException {
        record(windingRule == 0 ? "f" : "f*", null);
    }

    @Override
    public void fillAndStrokePath(int windingRule) throws IOException {
        record(windingRule == 0 ? "B" : "B*", null);
    }

    @Override
    public void shadingFill(COSName shadingName) throws IOException {
        record("sh", null);
    }

    private void record(String op, List<Float> operands) throws IOException {
        final Point2D p = getCurrentPoint();
        final Point2D.Float point = (p != null) ? new Point2D.Float((float) p.getX(), (float) p.getY()) : null;

        final float[] color = getGraphicsState().getNonStrokingColor().getComponents();
        final float alphaVal = readNonStrokingAlpha();

        commands.add(new ShapeCommand(op, operands, point, color, alphaVal));
    }

    private float readNonStrokingAlpha() {
        return invokeAlphaGetter("getNonStrokingAlphaConstant", "getNonStrokeAlphaConstant");
    }

    private float invokeAlphaGetter(final String... methodNames) {
        for (final String methodName : methodNames) {
            try {
                final Method method = getGraphicsState().getClass().getMethod(methodName);
                final Object value = method.invoke(getGraphicsState());
                if (value instanceof final Number number) {
                    return number.floatValue();
                }
            } catch (final ReflectiveOperationException e) {
                // Try the next known PDFBox variant.
            }
        }
        return 1.0f;
    }
}
