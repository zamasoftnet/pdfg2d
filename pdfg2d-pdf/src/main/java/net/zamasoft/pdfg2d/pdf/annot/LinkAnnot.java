package net.zamasoft.pdfg2d.pdf.annot;

import java.awt.geom.PathIterator;
import java.io.IOException;
import java.net.URI;

import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Link Annotation.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class LinkAnnot extends Annot {
	protected URI uri;

	/**
	 * Returns the link target URI, or {@code null} if not set.
	 * URIs starting with {@code #} are treated as named destinations within the
	 * same document.
	 *
	 * @return the target URI
	 */
	public URI getURI() {
		return this.uri;
	}

	/**
	 * Sets the link target URI.
	 * Use a URI starting with {@code #} (e.g., {@code URI.create("#destName")}) for
	 * in-document named destinations.
	 *
	 * @param uri the target URI
	 */
	public void setURI(final URI uri) {
		this.uri = uri;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void writeTo(final PDFOutput out, final PDFPageOutput pageOut) throws IOException {
		super.writeTo(out, pageOut);

		// Hide border lines
		out.writeName("Border");
		out.startArray();
		out.writeInt(0);
		out.writeInt(0);
		out.writeInt(0);
		out.endArray();
		out.lineBreak();

		out.writeName("Subtype");
		out.writeName("Link");
		out.lineBreak();

		final PDFParams.Version pdfVersion = pageOut.getPdfWriter().getParams().version();
		if (pdfVersion.v >= PDFParams.Version.V_1_6.v && !this.shape.equals(this.shape.getBounds2D())) {
			// Non-rectangular link area
			final double[] cord = new double[6];

			// Check if parallelogram
			int corners = 0;
			boolean bad = false, rect = true;
			double x = 0, y = 0;
			LOOP: for (final PathIterator i = this.shape.getPathIterator(null); !i.isDone(); i.next()) {
				final int type = i.currentSegment(cord);
				switch (type) {
					case PathIterator.SEG_LINETO:
						if (x != cord[0] && y != cord[1]) {
							rect = false;
						}
						x = cord[0];
						y = cord[1];
						++corners;
						break;
					case PathIterator.SEG_MOVETO:
						x = cord[0];
						y = cord[1];
						++corners;
						break;
					case PathIterator.SEG_QUADTO:
					case PathIterator.SEG_CUBICTO:
						bad = true;
						break LOOP;
					case PathIterator.SEG_CLOSE:
						break;
				}
			}

			// Place parallelogram
			if (!rect && !bad && corners == 4) {
				out.writeName("QuadPoints");
				out.startArray();
				final double pageHeight = pageOut.getHeight();
				for (final PathIterator i = this.shape.getPathIterator(null); !i.isDone(); i.next()) {
					final int type = i.currentSegment(cord);
					if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
						x = cord[0];
						y = cord[1];
						out.writeReal(x);
						out.writeReal(pageHeight - y);
					}
				}
				out.endArray();
				out.lineBreak();
			}
		}

		if (this.uri.toString().startsWith("#")) {
			out.writeName("Dest");
			out.writeText(this.uri.getFragment());
			out.lineBreak();
		} else {
			out.writeName("A");
			out.startHash();
			out.writeName("S");
			out.writeName("URI");
			out.lineBreak();
			out.writeName("URI");
			out.writeString(this.uri.toASCIIString());
			out.endHash();
			out.lineBreak();
		}
	}

	/**
	 * Returns a human-readable description of this link annotation.
	 *
	 * @return a string showing the target URI
	 */
	@Override
	public String toString() {
		return "Link: " + this.uri;
	}
}
