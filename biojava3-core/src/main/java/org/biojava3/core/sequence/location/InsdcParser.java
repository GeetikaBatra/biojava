/*
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 * Created on 01-21-2010
 */
package org.biojava3.core.sequence.location;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.biojava3.core.exceptions.ParserException;
import org.biojava3.core.sequence.AccessionID;
import org.biojava3.core.sequence.DataSource;
import org.biojava3.core.sequence.Strand;
import org.biojava3.core.sequence.location.template.Location;
import org.biojava3.core.sequence.location.template.Point;

/**
 * Parser for working with INSDC style locations. This class supports the
 * full range of location types generated by Genbank, INSDC and ENA.
 *
 * @author ayates
 * @author jgrzebyta
 */
public class InsdcParser {

    private final DataSource dataSource;

    public InsdcParser() {
        this(DataSource.ENA);
    }

    public InsdcParser(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Parses a location of the form Accession:1
     */
    private static final Pattern SINGLE_LOCATION = Pattern.compile(
            "\\A ([A-Za-z.0-9_]*?) :? ([<>]?) (\\d+) \\Z", Pattern.COMMENTS);

    /**
     * Parses a location of the form Accession:1..4 (also supports the ^
     * format and undefined locations)
     */
    private static final Pattern RANGE_LOCATION = Pattern.compile(
            "\\A ([A-Za-z.0-9_]*?) :? ([<>]?) (\\d+) ([.^]+) ([<>]?) (\\d+) \\Z", Pattern.COMMENTS);

    /**
     * Main method for parsing a location from a String instance
     *
     * @param locationString Represents a logical location
     * @return The parsed location
     * @throws ParserException thrown in the event of any error during parsing
     */
    public Location parse(String locationString) throws ParserException {
        try {
            return parse(new StringReader(locationString));
        }
        catch(IOException e) {
            throw new ParserException("Cannot parse the given location '"+
                    locationString+"'", e);
        }
    }

    /**
     * Reader based version of the parse methods.
     *
     * @param reader The source of the data; assumes that end of the reader
     * stream is the end of the location string to parse
     * @return The parsed location
     * @throws IOException Thrown with any reader error
     * @throws ParserException Thrown with any error with parsing locations
     */
    public Location parse(Reader reader) throws IOException, ParserException {
        List<Location> out = parse(reader, Strand.POSITIVE);
        if(out.size() > 1) {
            throw new ParserException("Too many locations parsed "+out);
        }
        else if(out.isEmpty()) {
            throw new ParserException("No locations parsed");
        }
        return out.get(0);
    }

    protected List<Location> parse(Reader reader, Strand strand) throws IOException, ParserException {
        StringBuilder sb = new StringBuilder();
        String typeOfJoin = null;
        List<Location> locationList = new ArrayList<Location>();

        int i = -1;
        while( (i = reader.read()) != -1 ) {
            char c = (char)i;
            switch(c) {
                case '(':
                    if(isComplement(sb)) {
                        locationList.addAll(parse(reader, strand.getReverse()));
                    }
                    else {
                        typeOfJoin = sb.toString();
                        List<Location> subs = parse(reader, strand);
                        locationList.add(LocationHelper.location(subs, typeOfJoin));
                    }
                    clearStringBuilder(sb);
                    break;
                case ',':
                case ')':
                    if(sb.length() > 0) {
                        locationList.add(parseLocation(sb.toString(), strand));
                    }
                    if( c == ')') {
                        return locationList;
                    }
                    clearStringBuilder(sb);
                    break;
                default:
                    if(!Character.isWhitespace(c)) {
                        sb.append(c);
                    }
                    break;
            }
        }

        if(sb.length() != 0) {
             locationList.add(parseLocation(sb.toString(), strand));
             clearStringBuilder(sb);
        }

        return locationList;
    }

    private boolean isComplement(StringBuilder sb) {
        return sb.toString().equals("complement");
    }

    private void clearStringBuilder(StringBuilder sb) {
        sb.delete(0, sb.length());
    }

    protected Location parseLocation(String location, Strand strand) {
        Matcher singleLoc = SINGLE_LOCATION.matcher(location);
        Matcher rangeLoc = RANGE_LOCATION.matcher(location);
        if(rangeLoc.matches()) {
            return parseRange(rangeLoc, strand);
        }
        else if(singleLoc.matches()) {
            return parseSingle(singleLoc, strand);
        }
        else {
            throw new ParserException("Location string does not match "
                    + "a single or range location");
        }
    }

    protected Location parseSingle(Matcher matcher, Strand strand) {
        String accession = matcher.group(1);
        String uncertain = matcher.group(2);
        String location = matcher.group(3);
        Point p = generatePoint(location, uncertain);
        if (accession == null || "".equals(accession)) {
            return new SimpleLocation(p, p, strand);
        }
        else {
            return new SimpleLocation(p, p, strand, getAccession(accession));
        }
    }

    protected Location parseRange(Matcher matcher, Strand strand) {
        String accession = matcher.group(1);
        String type = matcher.group(4);
        Point start = generatePoint(
                matcher.group(3),
                matcher.group(2));
        Point end = generatePoint(
                matcher.group(6),
                matcher.group(5));
        boolean betweenBases = "^".equals(type);
        if (accession == null || "".equals(accession)) {
            return new SimpleLocation(start, end, strand, false, betweenBases);
        }
        else {
            return new SimpleLocation(start, end, strand, betweenBases, getAccession(accession));
        }
    }

    protected Point generatePoint(String locationString, String uncertainString) {
        int location = Integer.valueOf(locationString);
        boolean unknown = false;
        boolean uncertain = (!"".equals(uncertainString));
        return new SimplePoint(location, unknown, uncertain);
    }

    protected AccessionID getAccession(String accession) {
        return new AccessionID(accession, getDataSource());
    }
}
