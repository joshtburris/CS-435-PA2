import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.log4j.Logger;

public class PA2_A {

    public static enum DocumentsCount {
        NUMDOCS
    }

    public static class ProfileAJob1Mapper extends Mapper<Object, Text, Text, IntWritable> {

        private Text word = new Text();
        private IntWritable one = new IntWritable(1);

        public void map(Object key, Text value, Context context) throws IOException,
                InterruptedException {

            if (value == null || value.toString().trim().isEmpty())
                return;

            String[] data = value.toString().split("<====>");
            String docID = data[1];

            if (data.length < 3)
                return;
            
            ArrayList<String> words = getWords(data[2]);
            
            for (String s : words) {
                word.set(docID + "\t" + s);
                context.write(word, one);
            }
            context.getCounter(PA2_A.DocumentsCount.NUMDOCS).increment(1);

        }

    }

    public static class ProfileAJob1Reducer extends Reducer<Text, IntWritable, Text, Text> {

        private Text docID = new Text();

        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {

            Integer sum = 0;

            for (IntWritable val : values) {
                sum += val.get();
            }

            String[] data = key.toString().split("\t");

            docID.set(data[0]);

            context.write(docID, new Text(data[1] + "\t" + sum));
        }

    }

    public static class ProfileAJob2Mapper extends Mapper<Object, Text, Text, Text> {

        public void map(Object key, Text value, Context context) throws IOException,
                InterruptedException {
            String[] data = value.toString().split("\t");
            context.write(new Text(data[0]), new Text(data[1] +"\t"+ data[2]));
        }

    }

    public static class ProfileAJob2Reducer extends Reducer<Text, Text, Text, Text> {

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            LinkedList<String> vList = new LinkedList<>();

            Integer max_k_f_kj = 0;
            for (Text val : values) {
                vList.add(val.toString());
                Integer f = Integer.parseInt(val.toString().split("\t")[1]);
                if (f > max_k_f_kj)
                    max_k_f_kj = f;
            }

            for (String val : vList) {
                String[] data = val.split("\t");
                String unigram = data[0];
                Integer f_ij = Integer.parseInt(data[1]);

                Double TF_ij = 0.5 + 0.5*((double)f_ij / (double)max_k_f_kj);

                context.write(key, new Text(unigram +"\t"+ TF_ij));
            }

        }

    }

    public static class ProfileAJob3Mapper extends Mapper<Object, Text, Text, Text> {

        private Text unigram = new Text(),
                docidUnigramTf = new Text();

        public void map(Object key, Text value, Context context) throws IOException,
                InterruptedException {

            String[] data = value.toString().split("\t");

            unigram.set(data[1]);
            docidUnigramTf.set(data[0] +"\t"+ data[1] +"\t"+ data[2]);

            context.write(unigram, docidUnigramTf);

        }

    }

    public static class ProfileAJob3Reducer extends Reducer<Text, Text, Text, Text> {

        private long N;
        private Text docID = new Text(),
                unigramTfIdf = new Text();

        public void setup(Context context) throws IOException, InterruptedException {
            N = context.getConfiguration().getLong(PA2_A.DocumentsCount.NUMDOCS.name(), 0);
        }

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            LinkedList<String> vList = new LinkedList<>();

            long n_i = 0;
            for (Text val : values) {
                vList.add(val.toString());
                ++n_i;
            }

            for (String val : vList) {
                String[] data = val.split("\t");

                double TF_ij = Double.parseDouble(data[2]);
                double IDF_i = Math.log10((double)N/(double)n_i);
                double TfIdf = TF_ij * IDF_i;

                docID.set(data[0]);
                unigramTfIdf.set(data[1] +"\t"+ TfIdf);
                context.write(docID, unigramTfIdf);
            }

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

        System.out.println("***************** Profile A Job 1 *****************");
        Counter documentCount = null;
        try {
            Configuration conf = new Configuration();
            Job job = Job.getInstance(conf, "Profile A Job 1");
            job.setNumReduceTasks(8);
            job.setJarByClass(PA2_A.class);
            job.setMapperClass(ProfileAJob1Mapper.class);
            job.setReducerClass(ProfileAJob1Reducer.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(IntWritable.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            FileInputFormat.addInputPath(job, new Path(args[0]));
            FileOutputFormat.setOutputPath(job, new Path("ProfileAJob1"));
            job.waitForCompletion(true);
            documentCount = job.getCounters().findCounter(DocumentsCount.NUMDOCS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("***************** Profile A Job 2 *****************");
        try {
            Configuration conf = new Configuration();
            Job job = Job.getInstance(conf, "Profile A Job 2");
            job.setNumReduceTasks(1);
            job.setJarByClass(PA2_A.class);
            job.setMapperClass(ProfileAJob2Mapper.class);
            job.setReducerClass(ProfileAJob2Reducer.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            FileInputFormat.addInputPath(job, new Path("ProfileAJob1"));
            FileOutputFormat.setOutputPath(job, new Path("ProfileAJob2"));
            job.waitForCompletion(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("***************** Profile A Job 3 *****************");
        try {
            Configuration conf = new Configuration();
            Job job = Job.getInstance(conf, "Profile A Job 3");
            job.setNumReduceTasks(1);
            job.setJarByClass(PA2_A.class);
            job.setMapperClass(ProfileAJob3Mapper.class);
            job.setReducerClass(ProfileAJob3Reducer.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            FileInputFormat.addInputPath(job, new Path("ProfileAJob2"));
            FileOutputFormat.setOutputPath(job, new Path(args[1]));
            job.getConfiguration().setLong(PA2_A.DocumentsCount.NUMDOCS.name(), documentCount.getValue());
            job.waitForCompletion(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
