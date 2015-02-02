/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.knaw.meertens.isocat2ccr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;
import javax.xml.transform.stream.StreamSource;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import nl.mpi.tla.schemanon.SaxonUtils;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author menzowindhouwer
 */
public class Main {

    private static void showHelp() {
        System.err.println("INF: isocat2ccr <options> -- <FILE> <FILE>");
        System.err.println("INF: <FILE>    map FILE containing <id> ISOcat <PID> pairs, for each <PID> encountered");
        System.err.println("INF:           the <id> will be looked up in the CCR and, when there is a match,");
        System.err.println("INF:           the <PID> will be replaced by the handle for the matching concept");
        System.err.println("INF: <FILE>    FILE in which to replace ISOcat PIDs with CCR handles");
        System.err.println("INF: isocat2ccr options:");
        System.err.println("INF: -r        registry endpoint (default: https://openskos.meertens.knaw.nl/ccr)");
        System.err.println("INF: -m        save the mapping file after the lookup in the CCR (default: false)");
        System.err.println("INF: -e=<ENC>  encoding used by the input file (default: system specific)");
        System.err.println("INF: -l=<EOL>  line separator to be used by the output (default: system specific)");
        System.err.println("INF:           Use 'r' and 'n' to compose the separator, e.g., -l=rn for a Windows EOL");
    }

    public static void main(String[] args) {
        String registry = "https://openskos.meertens.knaw.nl/ccr";
        Charset encoding = Charset.defaultCharset();
        String newline  = System.lineSeparator();
        Boolean saveMap = false;
        File mFile = null;
        File iFile = null;
        // check command line
        OptionParser parser = new OptionParser( "me:l:?*" );
        OptionSet options = parser.parse(args);
        if (options.has("r")) {
            registry = (String)options.valueOf("r");
        }
        if (options.has("m")) {
            saveMap = true;
        }
        if (options.has("e")) {
            encoding = Charset.forName((String)options.valueOf("e"));
        }
        if (options.has("l")) {
            String eol = (String)options.valueOf("l");
            newline = "";
            for (int i=0;i<eol.length();i++) {
                char c = eol.charAt(i);
                switch(c) {
                    case 'r': newline += "\r";break;
                    case 'n': newline += "\n";break;
                    default:
                        System.err.println("FTL: compose the new line separator by 'r' and 'n' characters!");
                        showHelp();
                        System.exit(1);
                }
            }
        }
        if (options.has("?")) {
            showHelp();
            System.exit(0);
        }
        
        List arg = options.nonOptionArguments();
        if (arg.size()!=2) {
            System.err.println("FTL: expected one map <FILE> and one input <FILE> as arguments!");
            showHelp();
            System.exit(1);
        }
        mFile = new File((String)arg.get(0));
        iFile = new File((String)arg.get(1));
        
        List<String> replace = new Vector<String>();
        String buffer = "";
        try {
            BufferedReader mIn = new BufferedReader(new InputStreamReader(new FileInputStream(mFile)));
            String line = null;
            while ((line = mIn.readLine()) != null) {
                String[] split = line.trim().split(",");
                String id = split[0];
                String pid = split[1];
                String handle = null;
                if (split.length>2)
                    handle = split[2];
                // No handle yet, do a lookup in the CCR
                if (handle == null || handle.trim().equals("")) {
                    URL url = new URL(registry+"/api/find-concepts/?q=uri:*C-"+id+"_*");
                    XdmNode resp = SaxonUtils.buildDocument(new StreamSource(url.toString()));
                    SaxonUtils.declareXPathNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
                    SaxonUtils.declareXPathNamespace("skos","http://www.w3.org/2004/02/skos/core#");
                    XdmValue set = SaxonUtils.evaluateXPath(resp, "/rdf:RDF/skos:Concept/@rdf:resource").evaluate();
                    if (set.size()==0) {
                        System.err.println("WRN: no CCR match found for id["+id+"] pid["+pid+"]!");
                    } else if (set.size()>1) {
                        System.err.println("ERR: multiple CCR matches found for id["+id+"] pid["+pid+"]!");
                        System.err.println("ERR: will use the first concept["+set.itemAt(0)+"]");
                        continue;
                    } else
                        handle = set.itemAt(0).getStringValue();
                }
                if (handle != null) {
                    replace.add(pid);
                    replace.add(handle);
                    System.err.println("DBG: ["+id+"] "+pid+" -> "+handle);
                }
                if (saveMap)
                    buffer += "" + id + "," + pid +(handle!=null?"," + handle:"") + System.getProperty("line.separator");
            }
            mIn.close();
            if (saveMap) {
                FileUtils.writeStringToFile(mFile, buffer, encoding);
                System.err.println("INF: saved mapping file["+mFile.getAbsolutePath()+"]");
            }
        } catch (Exception e) {
            System.err.println("FTL: map FILE["+mFile.getAbsolutePath()+"] couldn't be processed!");
            System.err.println("FTL: "+e.getMessage());
            System.exit(1);
        }
        
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(iFile)));
            String line = null;
            while ((line = in.readLine()) != null) {
                if (line.contains("http") && line.contains("isocat")){
                    for (int i = 0;i<replace.size();i++) {
                        line = line.replaceAll(Pattern.quote(replace.get(i)),replace.get(++i));
                    }
                }
                System.out.print(line);
                System.out.print(newline);
            }
        } catch (Exception e) {
            System.err.println("FTL: input FILE["+iFile.getAbsolutePath()+"] couldn't be read!");
            System.err.println("FTL: "+e.getMessage());
            System.exit(1);
        }        
    }

}
