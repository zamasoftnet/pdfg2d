package net.zamasoft.pdfg2d.font.table;

/**
 * Base interface for OpenType/TrueType font tables.
 * <p>
 * Defines integer tag constants for all known table types (e.g. {@link #HEAD},
 * {@link #HHEA}, {@link #CMAP}) as well as platform IDs, encoding IDs, and
 * name record IDs used throughout the OpenType specification.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface Table {

	// Table constants

	/** Tag for the OpenType BASE (Baseline data) table. */
	int BASE = 0x42415345;
	/** Tag for the PostScript CFF (Compact Font Format) table. */
	int CFF = 0x43464620;
	/** Tag for the DSIG (Digital Signature) table. */
	int DSIG = 0x44534947;
	/** Tag for the EBDT (Embedded Bitmap Data) table. */
	int EBDT = 0x45424454;
	/** Tag for the EBLC (Embedded Bitmap Location data) table. */
	int EBLC = 0x45424c43;
	/** Tag for the EBSC (Embedded Bitmap Scaling data) table. */
	int EBSC = 0x45425343;
	/** Tag for the OpenType GDEF (Glyph Definition data) table. */
	int GDEF = 0x47444546;
	/** Tag for the OpenType GPOS (Glyph Positioning data) table. */
	int GPOS = 0x47504f53;
	/** Tag for the OpenType GSUB (Glyph Substitution data) table. */
	int GSUB = 0x47535542;
	/** Tag for the OpenType JSTF (Justification data) table. */
	int JSTF = 0x4a535446;
	/** Tag for the LTSH (Linear Threshold) table. */
	int LTSH = 0x4c545348;
	/** Tag for the PostScript MMFX (Multiple Master Font Metrics) table. */
	int MMFX = 0x4d4d4658;
	/** Tag for the PostScript MMSD (Multiple Master Supplementary Data) table. */
	int MMSD = 0x4d4d5344;
	/** Tag for the OS/2 and Windows specific metrics table. */
	int OS_2 = 0x4f532f32;
	/** Tag for the PCLT (PCL 5) table. */
	int PCLT = 0x50434c54;
	/** Tag for the VDMX (Vertical Device Metrics) table. */
	int VDMX = 0x56444d58;
	/** Tag for the cmap (Character to Glyph Mapping) table. */
	int CMAP = 0x636d6170;
	/** Tag for the cvt (Control Value Table). */
	int CVT = 0x63767420;
	/** Tag for the fpgm (Font Program) table. */
	int FPGM = 0x6670676d;
	/** Tag for the Apple fvar (Font Variations) table. */
	int FVAR = 0x66766172;
	/** Tag for the gasp (Grid-fitting and Scan-conversion Procedure) table. */
	int GASP = 0x67617370;
	/** Tag for the glyf (Glyph Data) table. */
	int GLYF = 0x676c7966;
	/** Tag for the hdmx (Horizontal Device Metrics) table. */
	int HDMX = 0x68646d78;
	/** Tag for the head (Font Header) table. */
	int HEAD = 0x68656164;
	/** Tag for the hhea (Horizontal Header) table. */
	int HHEA = 0x68686561;
	/** Tag for the hmtx (Horizontal Metrics) table. */
	int HMTX = 0x686d7478;
	/** Tag for the kern (Kerning) table. */
	int KERN = 0x6b65726e;
	/** Tag for the loca (Index to Location) table. */
	int LOCA = 0x6c6f6361;
	/** Tag for the maxp (Maximum Profile) table. */
	int MAXP = 0x6d617870;
	/** Tag for the name (Naming) table. */
	int NAME = 0x6e616d65;
	/** Tag for the prep (CVT Program) table. */
	int PREP = 0x70726570;
	/** Tag for the post (PostScript Information) table. */
	int POST = 0x706f7374;
	/** Tag for the vhea (Vertical Metrics Header) table. */
	int VHEA = 0x76686561;
	/** Tag for the vmtx (Vertical Metrics) table. */
	int VMTX = 0x766d7478;
	/** Tag for the VORG (Vertical Origin) table. */
	int VORG = 0x564f5247;

	// Platform IDs

	/** Platform ID for Unicode. */
	short PLATFORM_UNICODE = 0;
	/** Platform ID for Macintosh. */
	short PLATFORM_MACINTOSH = 1;
	/** Platform ID for ISO. */
	short PLATFORM_ISO = 2;
	/** Platform ID for Microsoft. */
	short PLATFORM_MICROSOFT = 3;

	// Unicode Encoding IDs

	/** Unicode encoding ID for the Basic Multilingual Plane (BMP). */
	short ENCODING_BMP = 3;
	/** Unicode encoding ID for characters outside the BMP (non-BMP). */
	short ENCODING_NON_BMP = 4;
	/** Unicode encoding ID for Unicode Variation Sequences (UVS). */
	short ENCODING_UVS = 5;

	// Microsoft Encoding IDs

	/** Microsoft encoding ID: undefined. */
	short ENCODING_UNDEFINED = 0;
	/** Microsoft encoding ID for UCS-2. */
	short ENCODING_UCS2 = 1;
	/** Microsoft encoding ID for UCS-4. */
	short ENCODING_UCS4 = 10;

	// Macintosh Encoding IDs

	/** Macintosh encoding ID for Roman (Mac Roman). */
	short ENCODING_ROMAN = 0;
	/** Macintosh encoding ID for Japanese. */
	short ENCODING_JAPANESE = 1;
	/** Macintosh encoding ID for Chinese. */
	short ENCODING_CHINESE = 2;
	/** Macintosh encoding ID for Korean. */
	short ENCODING_KOREAN = 3;
	/** Macintosh encoding ID for Arabic. */
	short ENCODING_ARABIC = 4;
	/** Macintosh encoding ID for Hebrew. */
	short ENCODING_HEBREW = 5;
	/** Macintosh encoding ID for Greek. */
	short ENCODING_GREEK = 6;
	/** Macintosh encoding ID for Russian. */
	short ENCODING_RUSSIAN = 7;
	/** Macintosh encoding ID for RSymbol. */
	short ENCODING_R_SYMBOL = 8;
	/** Macintosh encoding ID for Devanagari. */
	short ENCODING_DEVANAGARI = 9;
	/** Macintosh encoding ID for Gurmukhi. */
	short ENCODING_GURMUKHI = 10;
	/** Macintosh encoding ID for Gujarati. */
	short ENCODING_GUJARATI = 11;
	/** Macintosh encoding ID for Oriya. */
	short ENCODING_ORIYA = 12;
	/** Macintosh encoding ID for Bengali. */
	short ENCODING_BENGALI = 13;
	/** Macintosh encoding ID for Tamil. */
	short ENCODING_TAMIL = 14;
	/** Macintosh encoding ID for Telugu. */
	short ENCODING_TELUGU = 15;
	/** Macintosh encoding ID for Kannada. */
	short ENCODING_KANNADA = 16;
	/** Macintosh encoding ID for Malayalam. */
	short ENCODING_MALAYALAM = 17;
	/** Macintosh encoding ID for Sinhalese. */
	short ENCODING_SINHALESE = 18;
	/** Macintosh encoding ID for Burmese. */
	short ENCODING_BURMESE = 19;
	/** Macintosh encoding ID for Khmer. */
	short ENCODING_KHMER = 20;
	/** Macintosh encoding ID for Thai. */
	short ENCODING_THAI = 21;
	/** Macintosh encoding ID for Laotian. */
	short ENCODING_LAOTIAN = 22;
	/** Macintosh encoding ID for Georgian. */
	short ENCODING_GEORGIAN = 23;
	/** Macintosh encoding ID for Armenian. */
	short ENCODING_ARMENIAN = 24;
	/** Macintosh encoding ID for Maldivian. */
	short ENCODING_MALDIVIAN = 25;
	/** Macintosh encoding ID for Tibetan. */
	short ENCODING_TIBETAN = 26;
	/** Macintosh encoding ID for Mongolian. */
	short ENCODING_MONGOLIAN = 27;
	/** Macintosh encoding ID for Geez. */
	short ENCODING_GEEZ = 28;
	/** Macintosh encoding ID for Slavic. */
	short ENCODING_SLAVIC = 29;
	/** Macintosh encoding ID for Vietnamese. */
	short ENCODING_VIETNAMESE = 30;
	/** Macintosh encoding ID for Sindhi. */
	short ENCODING_SINDHI = 31;
	/** Macintosh encoding ID for uninterpreted characters. */
	short ENCODING_UNINTERP = 32;

	// ISO Encoding IDs

	/** ISO encoding ID for ASCII. */
	short ENCODING_ASCII = 0;
	/** ISO encoding ID for ISO 10646. */
	short ENCODING_ISO10646 = 1;
	/** ISO encoding ID for ISO 8859-1. */
	short ENCODING_ISO8859_1 = 2;

	// Name IDs

	/** Name ID for the copyright notice string. */
	short NAME_COPYRIGHT_NOTICE = 0;
	/** Name ID for the font family name string. */
	short NAME_FONT_FAMILY_NAME = 1;
	/** Name ID for the font subfamily (style) name string. */
	short NAME_FONT_SUBFAMILY_NAME = 2;
	/** Name ID for the unique font identifier string. */
	short NAME_UNIQUE_FONT_IDENTIFIER = 3;
	/** Name ID for the full font name string. */
	short NAME_FULL_FONT_NAME = 4;
	/** Name ID for the version string. */
	short NAME_VERSION_STRING = 5;
	/** Name ID for the PostScript font name string. */
	short NAME_POSTSCRIPT_NAME = 6;
	/** Name ID for the trademark string. */
	short NAME_TRADEMARK = 7;

	/**
	 * Get the table type, as a table directory value.
	 *
	 * @return The table type
	 */
	int getType();
}
