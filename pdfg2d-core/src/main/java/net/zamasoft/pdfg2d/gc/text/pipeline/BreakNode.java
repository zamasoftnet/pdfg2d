package net.zamasoft.pdfg2d.gc.text.pipeline;

/**
 * A node in the Knuth-Plass box/glue/penalty line-breaking model. A line is a
 * prefix of the node stream; the breaker chooses penalties (or glue) at which
 * to end each line. Everything that influences breaking — justification,
 * kinsoku, hyphenation, ruby overhang — is expressed as glue and penalties, so
 * a single breaker serves them all.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public sealed interface BreakNode permits BreakNode.Box, BreakNode.Glue, BreakNode.Penalty {

	/** The node's natural width. */
	double width();

	/**
	 * An unbreakable piece of typeset material.
	 *
	 * @param width the advance width
	 */
	record Box(double width) implements BreakNode {
	}

	/**
	 * Flexible space that may stretch or shrink and is a break opportunity.
	 *
	 * @param width   the natural width
	 * @param stretch the extra width it may take under justification
	 * @param shrink  the width it may give up
	 */
	record Glue(double width, double stretch, double shrink) implements BreakNode {
	}

	/**
	 * A potential breakpoint with a cost. Infinite cost forbids a break;
	 * negative cost forces one. A flagged penalty marks a break that inserts
	 * material (a hyphen) when the line breaks there.
	 *
	 * @param width   the width contributed only when a line breaks here
	 * @param cost    the break penalty ({@link #INFINITY} forbids)
	 * @param flagged whether breaking here inserts material
	 */
	record Penalty(double width, int cost, boolean flagged) implements BreakNode {

		/** A cost that forbids breaking. */
		public static final int INFINITY = 10000;

		/** A cost that forces breaking. */
		public static final int FORCE = -10000;

		/** A mandatory break (end of paragraph / explicit line feed). */
		public static Penalty forced() {
			return new Penalty(0, FORCE, false);
		}

		/** A forbidden break (kinsoku: no break between these boxes). */
		public static Penalty forbidden() {
			return new Penalty(0, INFINITY, false);
		}
	}
}
