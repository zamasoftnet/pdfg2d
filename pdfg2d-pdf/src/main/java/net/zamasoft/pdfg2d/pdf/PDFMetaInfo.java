package net.zamasoft.pdfg2d.pdf;

/**
 * Metadata information for a PDF document, corresponding to the PDF
 * information dictionary (PDF spec section 14.3.3).
 * All string fields may be {@code null} if not set.
 * Date fields use milliseconds since the Unix epoch, or {@code -1} if not set.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class PDFMetaInfo {
	private String author;
	private String producer;
	private String creator;
	private String title;
	private String subject;
	private String keywords;
	private long creationDate = -1L;
	private long modDate = -1L;

	/**
	 * Returns the document author, or {@code null} if not set.
	 *
	 * @return the author string
	 */
	public String getAuthor() {
		return this.author;
	}

	/**
	 * Sets the document author.
	 *
	 * @param author the author string, or {@code null} to clear
	 */
	public void setAuthor(final String author) {
		this.author = author;
	}

	/**
	 * Returns the application that created the original document before conversion
	 * to PDF, or {@code null} if not set.
	 *
	 * @return the creator string
	 */
	public String getCreator() {
		return this.creator;
	}

	/**
	 * Sets the application that created the original document.
	 *
	 * @param creator the creator string, or {@code null} to clear
	 */
	public void setCreator(final String creator) {
		this.creator = creator;
	}

	/**
	 * Returns keywords associated with the document, or {@code null} if not set.
	 *
	 * @return the keywords string
	 */
	public String getKeywords() {
		return this.keywords;
	}

	/**
	 * Sets keywords associated with the document.
	 *
	 * @param keywords the keywords string, or {@code null} to clear
	 */
	public void setKeywords(final String keywords) {
		this.keywords = keywords;
	}

	/**
	 * Returns the application that converted the document to PDF, or {@code null}
	 * if not set.
	 *
	 * @return the producer string
	 */
	public String getProducer() {
		return this.producer;
	}

	/**
	 * Sets the application that converted the document to PDF.
	 *
	 * @param producer the producer string, or {@code null} to clear
	 */
	public void setProducer(final String producer) {
		this.producer = producer;
	}

	/**
	 * Returns the subject of the document, or {@code null} if not set.
	 *
	 * @return the subject string
	 */
	public String getSubject() {
		return this.subject;
	}

	/**
	 * Sets the subject of the document.
	 *
	 * @param subject the subject string, or {@code null} to clear
	 */
	public void setSubject(final String subject) {
		this.subject = subject;
	}

	/**
	 * Returns the document title, or {@code null} if not set.
	 *
	 * @return the title string
	 */
	public String getTitle() {
		return this.title;
	}

	/**
	 * Sets the document title.
	 *
	 * @param title the title string, or {@code null} to clear
	 */
	public void setTitle(final String title) {
		this.title = title;
	}

	/**
	 * Returns the document creation date as a Unix epoch timestamp in milliseconds,
	 * or {@code -1} if not set.
	 *
	 * @return the creation date timestamp, or {@code -1}
	 */
	public long getCreationDate() {
		return this.creationDate;
	}

	/**
	 * Sets the document creation date.
	 *
	 * @param creationDate the creation date as a Unix epoch timestamp in milliseconds,
	 *                     or {@code -1} to clear
	 */
	public void setCreationDate(final long creationDate) {
		this.creationDate = creationDate;
	}

	/**
	 * Returns the document modification date as a Unix epoch timestamp in
	 * milliseconds, or {@code -1} if not set.
	 *
	 * @return the modification date timestamp, or {@code -1}
	 */
	public long getModDate() {
		return this.modDate;
	}

	/**
	 * Sets the document modification date.
	 *
	 * @param modDate the modification date as a Unix epoch timestamp in milliseconds,
	 *                or {@code -1} to clear
	 */
	public void setModDate(final long modDate) {
		this.modDate = modDate;
	}
}
