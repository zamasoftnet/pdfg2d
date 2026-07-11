package net.zamasoft.pdfg2d.pdf.imposition;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.imposition.PrinterMarks;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Places logical pages onto a rows × columns grid of cells per sheet
 * (多面付け). Each logical page is written as a Form XObject the moment it
 * is closed — its content streams straight to the output — and sheets are
 * assembled from tiny placement dictionaries afterwards. Out-of-order
 * layouts such as saddle-stitch booklets therefore run with constant memory
 * regardless of the document length, which is exactly the workload the
 * fragmented output architecture was designed for.
 * <p>
 * Ordering strategies:
 * </p>
 * <ul>
 * <li>{@link Order#SEQUENTIAL} — fill the grid row-major; a sheet is emitted
 * whenever it is full (handouts, N-up printing).</li>
 * <li>{@link Order#REPEAT} — every logical page fills a whole sheet with
 * copies of itself (business cards, postcards, labels).</li>
 * <li>{@link Order#SADDLE_STITCH} — 中綴じ: fixed 1 × 2 grid; pages are
 * reordered into printer spreads at {@link #finish()}, with optional creep
 * compensation. Signature (折り丁) imposition with folding schemes is out of
 * scope by design.</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public class GridPDFImposition extends PDFImposition {

	/** How logical pages are assigned to grid cells. */
	public enum Order {
		/** Row-major, in reading order. */
		SEQUENTIAL,
		/** The same page in every cell of the sheet. */
		REPEAT,
		/** Saddle-stitch booklet spreads (implies a 1 × 2 grid). */
		SADDLE_STITCH,
		/**
		 * Cut &amp; stack (断裁後に重ねると通し順になる配置): cell {@code j}
		 * of sheet {@code s} carries page {@code j*S + s} for {@code S}
		 * sheets total — used for numbered tickets, forms and VDP output.
		 */
		CUT_AND_STACK
	}

	/** Binding side for saddle-stitch ordering. */
	public enum BoundSide {
		/** 左綴じ (Western/horizontal text). */
		LEFT,
		/** 右綴じ (Japanese vertical text). */
		RIGHT
	}

	private final int rows, cols;

	private final Order order;

	private BoundSide boundSide = BoundSide.LEFT;

	/**
	 * Saddle stitch creep compensation: content of sheet {@code s} (0 =
	 * outermost) shifts toward the spine by {@code creep * s} points to
	 * compensate for the fore-edge trimming of thick booklets.
	 */
	private double creep = 0;

	/** Length of the per-cell corner cut marks. */
	private double cellMarkLength = 8;

	/** Pending cells of the sheet being filled (SEQUENTIAL). */
	private final List<PDFGroupImage> pending = new ArrayList<>();

	/** All logical pages in order (SADDLE_STITCH; references only). */
	private final List<PDFGroupImage> pages = new ArrayList<>();

	/** Number of physical sheets emitted so far. */
	private int sheetNumber = 0;

	private PDFGC currentGC;

	/** State begun in {@link #nextPage()} and closed in {@link #closePage()}. */
	private GC.State currentGCState;

	private PDFGroupImage currentGroup;

	/**
	 * Creates a grid imposition.
	 *
	 * @param pdfWriter the writer to place sheets on
	 * @param rows      grid rows (ignored for {@link Order#SADDLE_STITCH})
	 * @param cols      grid columns (ignored for {@link Order#SADDLE_STITCH})
	 * @param order     the ordering strategy
	 */
	public GridPDFImposition(final PDFWriter pdfWriter, final int rows, final int cols, final Order order) {
		super(pdfWriter);
		if (order == Order.SADDLE_STITCH) {
			this.rows = 1;
			this.cols = 2;
		} else {
			if (rows < 1 || cols < 1) {
				throw new IllegalArgumentException("Grid must be at least 1x1.");
			}
			this.rows = rows;
			this.cols = cols;
		}
		this.order = order;
	}

	public final int getRows() {
		return this.rows;
	}

	public final int getCols() {
		return this.cols;
	}

	public final Order getOrder() {
		return this.order;
	}

	public final BoundSide getBoundSide() {
		return this.boundSide;
	}

	/** Sets the binding side used for saddle-stitch ordering. */
	public final void setBoundSide(final BoundSide boundSide) {
		this.boundSide = boundSide;
	}

	public final double getCreep() {
		return this.creep;
	}

	/**
	 * Sets the saddle-stitch creep compensation per sheet, in points.
	 * Content of the s-th sheet from the outside shifts toward the spine by
	 * {@code creep * s}.
	 */
	public final void setCreep(final double creep) {
		this.creep = creep;
	}

	/** Cell size including the bleed allowance on both sides. */
	private double cellWidth() {
		return this.pageWidth + this.trims.cuttingMargin() * 2.0;
	}

	private double cellHeight() {
		return this.pageHeight + this.trims.cuttingMargin() * 2.0;
	}

	/** X of the grid block's top-left corner (centered between the trims). */
	private double gridX() {
		final var inner = this.paperWidth - this.trims.left() - this.trims.right();
		return this.trims.left() + (inner - this.cols * this.cellWidth()) / 2.0;
	}

	private double gridY() {
		final var inner = this.paperHeight - this.trims.top() - this.trims.bottom();
		return this.trims.top() + (inner - this.rows * this.cellHeight()) / 2.0;
	}

	/** Sizes the paper to fit the grid plus the trim margins. */
	@Override
	public void fitPaperWidth() {
		this.paperWidth = this.cols * this.cellWidth() + this.trims.left() + this.trims.right();
	}

	@Override
	public void fitPaperHeight() {
		this.paperHeight = this.rows * this.cellHeight() + this.trims.top() + this.trims.bottom();
	}

	@Override
	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		try {
			// The logical page is a Form XObject sized to the cell (page plus
			// bleed); its BBox clips overflowing content, so no explicit clip
			// path is needed.
			this.currentGroup = this.pdfWriter.createGroupImage(this.cellWidth(), this.cellHeight());
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.currentGC = new PDFGC(this.currentGroup);
		this.currentGCState = this.currentGC.begin();
		final var m = this.trims.cuttingMargin();
		if (m != 0) {
			this.currentGC.transform(AffineTransform.getTranslateInstance(m, m));
		}
		return this.currentGC;
	}

	@Override
	public void closePage() throws GraphicsException {
		this.currentGCState.close();
		try {
			this.currentGC.close();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		final var group = this.currentGroup;
		this.currentGC = null;
		this.currentGCState = null;
		this.currentGroup = null;

		switch (this.order) {
			case SEQUENTIAL -> {
				this.pending.add(group);
				if (this.pending.size() >= this.rows * this.cols) {
					this.emitSheet(new ArrayList<>(this.pending), 0);
					this.pending.clear();
				}
			}
			case REPEAT -> {
				final var cells = new ArrayList<PDFGroupImage>();
				for (var i = 0; i < this.rows * this.cols; ++i) {
					cells.add(group);
				}
				this.emitSheet(cells, 0);
			}
			case SADDLE_STITCH, CUT_AND_STACK ->
				// Only the object references are retained; the page content
				// has already been streamed to the output.
				this.pages.add(group);
		}
	}

	@Override
	public void finish() throws GraphicsException {
		switch (this.order) {
			case SEQUENTIAL -> {
				if (!this.pending.isEmpty()) {
					this.emitSheet(new ArrayList<>(this.pending), 0);
					this.pending.clear();
				}
			}
			case REPEAT -> {
				// nothing buffered
			}
			case SADDLE_STITCH -> this.emitSaddleStitch();
			case CUT_AND_STACK -> this.emitCutAndStack();
		}
	}

	/**
	 * Emits the cut-and-stack layout: with {@code S} sheets, cell {@code j}
	 * of sheet {@code s} carries logical page {@code j*S + s + 1}, so
	 * cutting the pile and stacking the resulting stacks in cell order
	 * restores the page sequence.
	 */
	private void emitCutAndStack() throws GraphicsException {
		final var cells = this.rows * this.cols;
		final var sheets = (this.pages.size() + cells - 1) / cells;
		for (var sheet = 0; sheet < sheets; ++sheet) {
			final var content = new ArrayList<PDFGroupImage>(cells);
			for (var cell = 0; cell < cells; ++cell) {
				content.add(this.pageAt(cell * sheets + sheet + 1));
			}
			this.emitSheet(content, 0);
		}
		this.pages.clear();
	}

	/**
	 * Emits the saddle-stitch spreads: for {@code n} pages (padded to a
	 * multiple of 4), sheet {@code s} carries the sides
	 * {@code [n-2s, 1+2s]} (front) and {@code [2+2s, n-1-2s]} (back) for
	 * left binding, mirrored for right binding.
	 */
	private void emitSaddleStitch() throws GraphicsException {
		final var n = ((this.pages.size() + 3) / 4) * 4;
		final var sheets = n / 4;
		for (var s = 0; s < sheets; ++s) {
			final var shift = this.creep * s;
			// 1-based logical page numbers of the four sides
			final int frontOuter = n - 2 * s;
			final int frontInner = 1 + 2 * s;
			final int backInner = 2 + 2 * s;
			final int backOuter = n - 1 - 2 * s;
			if (this.boundSide == BoundSide.LEFT) {
				this.emitSheet(java.util.Arrays.asList(this.pageAt(frontOuter), this.pageAt(frontInner)), shift);
				this.emitSheet(java.util.Arrays.asList(this.pageAt(backInner), this.pageAt(backOuter)), shift);
			} else {
				this.emitSheet(java.util.Arrays.asList(this.pageAt(frontInner), this.pageAt(frontOuter)), shift);
				this.emitSheet(java.util.Arrays.asList(this.pageAt(backOuter), this.pageAt(backInner)), shift);
			}
		}
		this.pages.clear();
	}

	/** Returns the 1-based page, or {@code null} for padding blanks. */
	private PDFGroupImage pageAt(final int pageNumber1Based) {
		final var i = pageNumber1Based - 1;
		return (i >= 0 && i < this.pages.size()) ? this.pages.get(i) : null;
	}

	/**
	 * Emits one physical sheet with the given cells (row-major;
	 * {@code null} cells stay blank).
	 *
	 * @param cells      the cell contents
	 * @param creepShift horizontal shift toward the spine (saddle stitch)
	 */
	private void emitSheet(final List<PDFGroupImage> cells, final double creepShift) throws GraphicsException {
		++this.sheetNumber;
		try {
			final var pageOut = this.pdfWriter.nextPage(this.paperWidth, this.paperHeight);
			final var m = this.trims.cuttingMargin();
			final var gridX = this.gridX();
			final var gridY = this.gridY();

			// Page boxes: the trimmed grid block and its bleed
			if (this.pageBoxes && this.pdfWriter.getParams().version().v >= PDFParams.Version.V_1_4.v) {
				final var trimX = gridX + m;
				final var trimY = gridY + m;
				final var trimW = this.cols * this.cellWidth() - m * 2.0;
				final var trimH = this.rows * this.cellHeight() - m * 2.0;
				pageOut.setTrimBox(new Rectangle2D.Double(trimX, trimY, trimW, trimH));
				pageOut.setBleedBox(new Rectangle2D.Double(gridX, gridY,
						this.cols * this.cellWidth(), this.rows * this.cellHeight()));
			}

			try (final var gc = new PDFGC(pageOut)) {
				for (var i = 0; i < cells.size(); ++i) {
					final var cell = cells.get(i);
					if (cell == null) {
						continue;
					}
					final var row = i / this.cols;
					final var col = i % this.cols;
					final var x = gridX + col * this.cellWidth();
					final var y = gridY + row * this.cellHeight();
					// Creep: for the left cell of a spread the spine is on
					// the right, so shifting toward the spine is +x; for the
					// right cell it is -x.
					final double dx;
					if (creepShift != 0 && this.cols == 2) {
						dx = (col == 0) ? creepShift : -creepShift;
					} else {
						dx = 0;
					}
					try (final var gcState = gc.begin()) {
						gc.transform(AffineTransform.getTranslateInstance(x + dx, y));
						cell.drawTo(gc);
					}
				}

				// Marks: compact cut marks around each trimmed cell
				if (this.crop) {
					for (var row = 0; row < this.rows; ++row) {
						for (var col = 0; col < this.cols; ++col) {
							final var cellX = gridX + col * this.cellWidth();
							final var cellY = gridY + row * this.cellHeight();
							PrinterMarks.drawCellCorners(gc,
									new Rectangle2D.Double(cellX + m, cellY + m,
											this.pageWidth, this.pageHeight),
									m, this.cellMarkLength);
						}
					}
				}

				// Note line inside the top trim margin
				if (this.note != null && this.trims.top() > 0) {
					final var text = this.note.format(new Object[] { String.valueOf(this.sheetNumber) });
					final var fontSize = this.trims.top() / 6.0;
					PrinterMarks.drawText(gc, this.notePolicy, fontSize, text, gridX,
							this.trims.top() - m - fontSize, this.paperWidth / 2.0);
				}
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}
}
