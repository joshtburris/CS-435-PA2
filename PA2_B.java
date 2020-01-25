import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;
import org.apache.log4j.Logger;

public class PA2_B {

    public static class ProfileBInput1Mapper extends Mapper<Object, Text, Text, Text> {

        private Text docID = new Text();

        public void map(Object key, Text value, Context context) throws IOException,
                InterruptedException {

            if (value == null || value.toString().trim().isEmpty())
                return;

            String[] data = value.toString().split("\t");
            docID.set(data[0]);

            context.write(docID, new Text("A\t"+ data[1] +"\t"+ data[2]));

        }

    }

    public static class ProfileBInput2Mapper extends Mapper<Object, Text, Text, Text> {

        private Text docID = new Text();

        public void map(Object key, Text value, Context context) throws IOException,
                InterruptedException {

            if (value == null || value.toString().trim().isEmpty())
                return;

            String[] data = value.toString().split("<====>");
            docID.set(data[1]);

            if (data.length > 2)
                context.write(docID, new Text("B\t"+ data[2]));

        }

    }

    public static class ProfileBReducer extends Reducer<Text, Text, Text, Text> {

        private Integer docID = -1;
        private Comparator tfidfComparator = new Comparator<String>() {
            public int compare(String s0, String s1) {
                String[] split0 = s0.split("\t");
                String[] split1 = s1.split("\t");
                Double d0 = Double.parseDouble(split0[0]);
                Double d1 = Double.parseDouble(split1[0]);
                int compare = -d0.compareTo(d1);
                if (compare == 0)
                    compare = split0[1].compareTo(split1[1]);
                return compare;
            }
        };
        private Comparator unigramComparator = new Comparator<String>() {
            public int compare(String s0, String s1) {
                return s0.compareTo(s1);
            }
        };

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            docID = Integer.parseInt(key.toString());
            SortedMap<String, Double> tfidfTable = new TreeMap<>();
            TreeMap<String, Integer> top3Sentences = new TreeMap<>(tfidfComparator);
            String[] sentences = null;

            for (Text val : values) {
                String[] data = val.toString().split("\t", 3);
                if (data[0].compareTo("A") == 0) {
                    String word = data[1];
                    double tfidf = Double.parseDouble(data[2]);

                    tfidfTable.put(word, tfidf);
                } else {
                    String sent = data[1];
                    sentences = sent.split("\\. ");
                }
            }

            if (sentences == null || sentences.length == 0)
                return;

           for (int i = 0; i < sentences.length; ++i) {
                String s = sentences[i].trim();

                ArrayList<String> words = getWords(s);

                if (words.isEmpty())
                    continue;

                TreeMap<String, Double> top5Unigrams = new TreeMap<>(tfidfComparator);
                for (String word : words) {
                    Double tfidf = tfidfTable.get(word);
                    if (tfidf == null)
                        continue;
                    top5Unigrams.put(tfidf +"\t"+ word, 0.0);
                    if (top5Unigrams.size() > 5)
                        top5Unigrams.pollLastEntry();
                }

                double total = 0;
                for (String keyEntry : top5Unigrams.keySet()) {
                    total += Double.parseDouble(keyEntry.split("\t")[0]);
                    //context.write(new Text(docID.toString()), new Text(keyEntry));
                }

                top3Sentences.put(total +"\t"+ s, i);
                //context.write(new Text(docID.toString()), new Text(total +"\t"+ s));
                if (top3Sentences.size() > 3)
                    top3Sentences.pollLastEntry();
            }

            TreeMap<Integer, String> sorted = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : top3Sentences.entrySet()) {
                sorted.put(entry.getValue(), entry.getKey().split("\t")[1]);
            }
            String output = "";
            for (Map.Entry<Integer, String> entry : sorted.entrySet()) {
                output += entry.getValue() + ". ";
            }

            if (output.compareTo("") != 0)
                context.write(new Text(docID.toString()), new Text(output.substring(0,
                    output.length()-1)));

        }

    }

    private static ArrayList<String> getWords(String sentence) {
        ArrayList<String> words = new ArrayList<>();
        StringTokenizer itr = new StringTokenizer(sentence);
        while (itr.hasMoreTokens()) {
            String token = itr.nextToken();
            StringBuilder builder = new StringBuilder(token.length());

            for (char c : token.toCharArray()) {
                if ((c >= 48 && c <= 57) || (c >= 65 && c <= 90)
                        || (c >= 97 && c <= 122)) {
                    builder.append(Character.toLowerCase(c));
                }
            }

            if (!builder.toString().isEmpty())
                words.add(builder.toString());
        }

        return words;
    }

    public static void main(String[] args) {

        System.out.println("***************** Profile B *****************");
        try {
            Configuration conf = new Configuration();
            Job job = Job.getInstance(conf, "Profile B");
            job.setNumReduceTasks(10);
            job.setJarByClass(PA2_B.class);
            job.setMapperClass(ProfileBInput1Mapper.class);
            job.setReducerClass(ProfileBReducer.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class,
                ProfileBInput1Mapper.class);
            MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class,
                ProfileBInput2Mapper.class);
            FileOutputFormat.setOutputPath(job, new Path(args[2]));
            job.waitForCompletion(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
