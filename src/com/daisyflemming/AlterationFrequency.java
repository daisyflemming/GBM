package com.daisyflemming;

import com.google.common.base.Joiner;
import com.opencsv.CSVReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/*
 * @author: Daisy Flemming
 * Note:
 * Assuming the cBio data base is up and this app will only handle this particular combination
 * of data set
 *   cmd=getProfileData
 *   genetic_profile_id=[gbm_tcga_mutations | gbm_tcga_gistic]
 *   case_set_id=gbm_tcga_cnaseq
 *   id_type=gene_symbol
 *   gene_list=gene symbols separated by a comma
 *
 * Preferably, I would want to validate the gene symbols of my genes aganist cBio
 * to check if they are valid or have other names before making a request for data.
 * e.g. CD62L is an alias of SELL, if you enter CD62L as the gene symbol,
 * the data that returns is tagged as SELL. It can be confusing.
 *
 *  1. If the gene symbol is valid and mutation data is available, the data is followed by
 *   the following 2 lines with #hashtag and similarly for gistic data.
 *   # DATA_TYPE	 Mutations
 *   # COLOR_GRADIENT_SETTINGS	 MUTATION_EXTENDED
 *  2. If the gene symbol is valid but no data is available, a row of "NaN" is returned
 *  3. If gene symbol is invalid, the following line will appear as first line
 *     # Warning:  Unknown gene:  TP
 *  4. We have two data set: Cn & Mut. We want to keep track of the following
 *     a. the gene alteration frequency in each data set
 *     b. the overall alteration frequency in BOTH data set  per gene
 *     c. the combined overall alteration frequency of the given gene set in BOTH data set
 *  5. When calculating the overall alteration frequency, only includes samples that appears in both CN & Mut
 * data set (based on spec). Otherwise any further correlation won't make sense.
 *
 * http://www.cbioportal.org/index.do?cancer_study_list=gbm_tcga&cancer_study_id=gbm_tcga&genetic_profile_ids_PROFILE_MUTATION_EXTENDED=gbm_tcga_mutations&genetic_profile_ids_PROFILE_COPY_NUMBER_ALTERATION=gbm_tcga_gistic&Z_SCORE_THRESHOLD=2.0&RPPA_SCORE_THRESHOLD=2.0&data_priority=0&case_set_id=gbm_tcga_cnaseq&case_ids=&gene_set_choice=user-defined-list&gene_list=TP53+MDM2+MDM4&clinical_param_selection=null&tab_index=tab_visualize&Action=Submit
 *
 */
public class AlterationFrequency {
    private StringBuilder outputBuffer = new StringBuilder();

    public static final String warningMsg = "# Warning:";
    public static final String profileMut = "gbm_tcga_mutations";
    public static final String profileGistic = "gbm_tcga_gistic";
    public static final String wsUrl = "http://www.cbioportal.org/webservice.do?";
    public static final String query = "cmd=getProfileData&case_set_id=gbm_tcga_cnaseq&id_type=gene_symbol";

    // commonSamplesIndex contain list of samples that are found in both CN and Mut data set
    private List<String> commonSamplesIndex = new ArrayList<String>();
    /*
     * countPerGeneInCommonSamples.get(geneSymbol) contains  counter for each common sample in a given geneSymbol.
     * When there is an alteration in CN or Mut of a sample, we will increment the count for that sample by 1.
     * When there is an alteration in both CN & Mut of a sample, we will increment the count for that sample by 2.
     * This way, we can calculate the tally the overall frequency among our genes later
     */
    private HashMap<String, int[]> countPerGeneInCommonSamples = new HashMap<String, int[]>();
    // contains gene alteration frequency in Mut
    private HashMap<String, Integer> mutFreqMap = new HashMap<String, Integer>();
    // contains gene alteration frequency in CN
    private HashMap<String, Integer> cnFreqMap = new HashMap<String, Integer>();

    private HttpURLConnection conn;
    private BufferedReader rd;

    public static void main(String[] args) {
        // args contains gene names
        AlterationFrequency main = new AlterationFrequency();
        try {
            main.doIt(args);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Retrieves Mutations and Gistic Copy Number data from cBio web service
     * and calulates the overall alteration frequency. Result will be written
     * to stdOut.
     *
     * @param args are gene symbols from command line
     * @return void
     * @throws IOException when httpConnection to cBio web service fails.
     */
    public void doIt(String[] args) throws IOException {
        //1. concatenate geneSymbols into one string with a separator
        String geneSymbols = Joiner.on(",").skipNulls().join(args);
        //2. get a pointer to both set of data
        CSVReader mutReader = cBioConnect(profileMut, geneSymbols);
        CSVReader cnReader = cBioConnect(profileGistic, geneSymbols);

        // 3. define common samples
        HashSet<String> commonSamples = new HashSet<String>();
        String[] mutHeaders = mutReader.readNext();
        String[] mutSampleNames = Arrays.copyOfRange(mutHeaders, 2, mutHeaders.length);
        commonSamples.addAll(Arrays.asList(mutSampleNames));

        String[] cnHeaders = cnReader.readNext();
        String[] cnSampleNames = Arrays.copyOfRange(cnHeaders, 2, cnHeaders.length);
        commonSamples.addAll(Arrays.asList(cnSampleNames));
        commonSamplesIndex = new ArrayList<String>(commonSamples);

        //4. get mutation frequency based on given geneSymbols
        getAlterationFreq(profileMut, mutReader, mutHeaders);
        //5. get cn alteration frequency based on given geneSymbols
        getAlterationFreq(profileGistic, cnReader, cnHeaders);
        //6. assemble outputBuffer based on data collected in cnFreq, mutFreq, countPerGeneInCommonSamples, overallFreq
        prepareOutput(args);
        //7. print out message to stdOut
        System.out.println(outputBuffer.toString());
        rd.close();
        conn.disconnect();

    }

    /**
     * Given a dataType and a set of genes, return a HashMaph that represents the alteration frequency
     * of each gene in the dataType
     * @param dataType = [gbm_tcga_mutations | gbm_tcga_gistic]
     * @param geneSymbols = geneSymbols concatenated by a comma
     * @return CSVReader pointer to the given data set
     * @throws IOException when httpConnection to cBio web service fails.
     */
    public CSVReader cBioConnect(String dataType, String geneSymbols) throws IOException {
        // 0. create urlString
        StringBuilder builder = new StringBuilder();
        builder.append(wsUrl).append(query)
                .append("&genetic_profile_id=").append(dataType)
                .append("&gene_list=").append(geneSymbols);
        //System.out.println(builder.toString());

        // 1. do a GET request
        URL url = new URL(builder.toString());
        conn = (HttpURLConnection) url.openConnection();
        // throws exception when web service request fails
        if (conn.getResponseCode() != 200) {
            throw new IOException(conn.getResponseMessage());
        }

        //2. Buffering the response and use CVSReader to process the buffer
        rd = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        CSVReader reader = new CSVReader(rd, '\t');
        String[] firstLine;
        String[] secondLine;
        builder = new StringBuilder();

        //3. read 1st line and 2nd line and looking for warning
        // if all is well, the 1st two lines are response summary that begin with a hashtag
        firstLine = reader.readNext();
        if (firstLine[0].indexOf(warningMsg) >= 0) {
            // attached warning message for output
            outputBuffer.append("When processing ").append(dataType)
                    .append(", the following warning was observed:\n")
                    .append(firstLine[0]).append("\n");
            firstLine = reader.readNext();
        }
        // Otherwise 1st line is DataType
        // read 2nd line, it is color setting and we don't care about this
        secondLine = reader.readNext();

        //4. From this onwards, reader contains data with a header
        return reader;
    }

    /**
     * Given a dataType and a set of genes, return a HashMaph that represents the alteration frequency
     * of each gene in the dataType
     * @param dataType = [gbm_tcga_mutations | gbm_tcga_gistic]
     * @param reader pointer to the given data set
     * @param headers contains samples in the given data set
     * @return void
     * @throws IOException when httpConnection to cBio web service fails.
     */
    public void getAlterationFreq(String dataType, CSVReader reader, String[] headers) throws IOException {

        //1. onwards are data, process them
        String[] nextLine;
        int altFreq = 0;
        while ((nextLine = reader.readNext()) != null) {
            String geneSymbol = nextLine[1];
            if (countPerGeneInCommonSamples.get(geneSymbol) == null){
                countPerGeneInCommonSamples.put(geneSymbol, new int[commonSamplesIndex.size()]);
            }
            // nextLine[] is an array of values from the line
            if (dataType.equals(profileMut)) {
                mutFreqMap.put(geneSymbol, getMutationFrequency(headers, nextLine));
            }
            if (dataType.equals(profileGistic)) {
                // hopefully, we already have mutation data to set the smaple set
                cnFreqMap.put(geneSymbol, getGisticAlteration(headers, nextLine));
            }
        }

    }

    /**
     * Given an array of sampleName and Mut data of a gene, add our gene overall alteration frequency
     * to countPerGeneInCommonSamples. Also returns the mut frequency of our gene.
     * @param headers = contains geneId,geneSymbol and a list of sammple names in this order
     * @param data = Gene mutation data across samples
     * @return mutation frequency of a given gene
     */
    public int getMutationFrequency(String[] headers, String[] data) {
        // get the altFreq for the gene that we are processing
        // we will update it as we process the data
        String geneSymbol = data[1];
        //int[] geneAltFreq = new int[headers.length -2];

        int count = 0;
        for (int i = 2; i < data.length; i++) {
            if (!("0".equals(data[i]) || "NaN".equals(data[i]))) {
                count++;
                // get the index of the given sample in the commonSamplesIndex and increment both counters
                int index = commonSamplesIndex.indexOf(headers[i]);
                if (index >= 0) {
                    countPerGeneInCommonSamples.get(geneSymbol)[index]++;
                }
            }
        }

        int percentage = (int) Math.round(100.0 * count / (data.length - 2));
        return percentage;
    }

    /**
     * Given an array of sampleName and CN data of a gene, add our gene overall alteration frequency
     * to countPerGeneInCommonSamples. Also returns the cn frequency of our gene.
     * of each gene in the dataType
     * @param headers = contains geneId,geneSymbol and a list of sammple names in this order
     * @param data = Gene CNV across samples
     * @return CNV frequency of a given gene
     */
    public int getGisticAlteration(String[] headers, String[] data) {
        // get the altFreq for the gene that we are processing
        // we will update it as we process the data
        // data[1] = geneSymbol
        String geneSymbol = data[1];

        int count = 0;
        for (int i = 2; i < data.length; i++) {
            if (Integer.parseInt(data[i]) >= 2 || Integer.parseInt(data[i]) <= -2) {
                count++;
                int index = commonSamplesIndex.indexOf(headers[i]);
                if (index >= 0) {
                    countPerGeneInCommonSamples.get(geneSymbol)[index]++;
                }
            }
        }
        int percentage = (int) Math.round(100.0 * count / (data.length - 2));
        return percentage;
    }

    /**
     * Given a counter of sample alteration, return the frequency
     * @param overallFreq contains count of alteration per samples
     * @return frequency
     */
    public int tally(int[] overallFreq) {
        int count = 0;
        for (int i : overallFreq) {
            if (i > 0) {
                count++;
            }
        }
        //System.out.println("**tally: "+Math.round(100 * count / overallFreq.length));
        return (int) Math.round(100.0 * count / overallFreq.length);
    }

    /**
     * Make use of  mutFreqMap, cnFreqMap, countPerGeneInCommonSamples of a gene set to provide a summary of
     * alteration frequency of the gene set involved.
     * @return void
     */
    public void prepareOutput(String[] args) {
        List<String> noData = new ArrayList<String>();
        outputBuffer.append("\n");
        //4a. show tally,  if there is only one gene, be specific
        if (countPerGeneInCommonSamples.size() == 1) {
            String geneSymbol = countPerGeneInCommonSamples.keySet().iterator().next();
            int tallyMut = mutFreqMap.get(geneSymbol);
            int tallyCN = cnFreqMap.get(geneSymbol);
            if (tallyMut > 0) {
                outputBuffer.append(geneSymbol).append(" is mutated in ")
                        .append(tallyMut).append("% of all cases.\n");
            }
            if (tallyCN > 0) {
                outputBuffer.append(geneSymbol).append(" is copy number altered in ")
                        .append(tallyCN).append("% of all cases.\n");
            }
            outputBuffer.append("Total % of cases where ")
                    .append(geneSymbol).append(" is altered by either mutation or copy number alteration: ")
                    .append(tally(countPerGeneInCommonSamples.get(geneSymbol))).append("% of all cases.\n");
        }
        //4b. if there is more than one genes, refer them as gene set
        else {
            int[] overallCount = new int[commonSamplesIndex.size()];
            for (String geneSymbol : args) {
                int[] countPerGene = countPerGeneInCommonSamples.get(geneSymbol);
                if (countPerGene !=null && countPerGene.length>1) {
                    outputBuffer.append(geneSymbol).append(" is altered in ")
                            .append(tally(countPerGene))
                            .append("% of cases.\n");

                    for (int i = 0; i < countPerGene.length; i++) {
                        if (countPerGene[i] > 0) {
                            overallCount[i]++;
                        }
                    }
                }
                else{
                    noData.add(geneSymbol);
                }
            }
            outputBuffer.append("\nThe gene set is altered in ")
                    .append(tally(overallCount)).append("% of all cases.\n");
            if (noData.size()>0) {
                outputBuffer.append("\nWe do not have data for the following genes:\n ")
                        .append(Joiner.on(",").skipNulls().join(noData));
            }

        }

    }

}