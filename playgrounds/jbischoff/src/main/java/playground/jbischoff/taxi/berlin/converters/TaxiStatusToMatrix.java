package playground.jbischoff.taxi.berlin.converters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.utils.io.tabularFileParser.TabularFileHandler;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;
import org.matsim.matrices.Matrices;
import org.matsim.matrices.MatricesWriter;
import org.matsim.matrices.Matrix;

public class TaxiStatusToMatrix
{

    /**
     * @param args
     */
    private Matrices matrices;
    private String folder;
    public static void main(String[] args)
    {
       TaxiStatusToMatrix m = new TaxiStatusToMatrix("C:\\local_jb\\data\\OD\\status\\");
       m.read();
       m.write();
    }


    public void write()
    {
        new MatricesWriter(this.matrices).write(folder+"statusMatrix.xml");
    }


    public TaxiStatusToMatrix(String folder)
    {
        this.folder = folder;
        this.matrices = new Matrices();
    }
    
    
    
    public void read()
    {
        List<File> files = this.listFilesForFolder(new File(folder));
        for (File f : files){
            String shortName = f.getName().substring(0, f.getName().lastIndexOf(".")).split("_")[1];
            Matrix currentMatrix =  this.matrices.createMatrix(shortName, null);
            TaxiStatusParser tsp = new TaxiStatusParser(currentMatrix);
            this.read(f.getAbsolutePath(), tsp);
          
        }
    }
 

  

    private List<File> listFilesForFolder(final File folder) {
        List<File> files = new ArrayList<File>();
        for (final File fileEntry : folder.listFiles()) {
            if (!fileEntry.isDirectory()) 
            {
                if (!fileEntry.getName().endsWith(".dat")) continue;
                files.add(fileEntry);
            }
        }
        return files;
    }
    private void read(String file, TabularFileHandler handler) {
        TabularFileParserConfig config = new TabularFileParserConfig();
        config.setDelimiterTags(new String[]{"\t"," "});
        config.setFileName(file);
        config.setCommentTags(new String[]{"#"});
        new TabularFileParser().parse(config, handler);
    }
    
}
class TaxiStatusParser implements TabularFileHandler{
    
    Matrix matrix;
    
    public TaxiStatusParser(Matrix matrix)
    {
        this.matrix = matrix;
    }
    @Override
    public void startRow(String[] row)
    {
        
        try{
            Id lor = new IdImpl(row[0]);
       for (int i = 1; ;i=i+2){
           Id statusId = new IdImpl(row[i]);
           double vehicles = Double.parseDouble(row[i+1]);
           this.matrix.createEntry(lor, statusId, vehicles);
       }
        }
        catch (ArrayIndexOutOfBoundsException e){
            
        }
    }
    }