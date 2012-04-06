package com.senseidb.search.relevance;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.json.JSONException;
import org.json.JSONObject;

import com.browseengine.bobo.api.BoboIndexReader;
import com.senseidb.search.query.AbstractScoreAdjuster;
import com.senseidb.search.relevance.CompilationHelper.DataTable;
import com.senseidb.search.relevance.CustomScorer.scoreModifier;

public class RelevanceQuery extends AbstractScoreAdjuster
{
  
  private static final long serialVersionUID = 1L;
  private static Logger logger = Logger.getLogger(RelevanceQuery.class);
  
  protected final Query       _query;
  private DataTable           _dt     = null;
  private CustomMathModel     _cModel = null;
  
  
  public RelevanceQuery(Query query, JSONObject relevance) throws JSONException
  {
    super(query);
    _query = query;
    _dt = new DataTable();
    _cModel = CompilationHelper.createCustomMathScorer(relevance, _dt);
  }
 


  @Override
  protected Scorer createScorer(final Scorer innerScorer,
                                IndexReader reader,
                                boolean scoreDocsInOrder,
                                boolean topScorer) throws IOException
  {
    if(_cModel == null)
      return innerScorer;
    
    if (reader instanceof BoboIndexReader ){
      BoboIndexReader boboReader = (BoboIndexReader)reader;
      CustomScorer cScorer = null;
      try{
        cScorer = new CustomScorer(innerScorer, boboReader, _cModel, _dt);
      }catch(Exception e){
        logger.info(e.getMessage());
        cScorer = null;
      }
      
      if(cScorer == null)
        return innerScorer;
      else 
        return cScorer;
    }
    else{
      return innerScorer;
    }
  }



  @Override
  protected Explanation createExplain(Explanation innerExplain,
                                      IndexReader reader,
                                      int doc)
  {
    if(_cModel == null || _dt == null)
      return createDummyExplain(innerExplain, "cModel is null, return innerExplanation.");
    
    if (reader instanceof BoboIndexReader ){
      
      Explanation finalExpl = new Explanation();
      finalExpl.addDetail(innerExplain);
      
      try{
        scoreModifier sModifier = new scoreModifier( (BoboIndexReader)reader, _cModel, _dt);
        float value = sModifier.score(innerExplain.getValue(), doc);
        finalExpl.setValue(value);
        finalExpl.setDescription("Custom score: "+ value + "  function:"+ _dt.funcBody);
        
        return finalExpl;
        
      }catch(Exception e){
        return createDummyExplain(innerExplain, "Can not create scoreModifier. Use the original inner score.");
      }
    }
    else{
      return createDummyExplain(innerExplain, "Non-Bobo reader with custom scorer. Should not arrive here.");
    }
  }
  
  private Explanation createDummyExplain(Explanation innerExplain, String message)
  {
    Explanation finalExpl = new Explanation();
    finalExpl.addDetail(innerExplain);
    finalExpl.setDescription(message);
    finalExpl.setValue(innerExplain.getValue());
    return finalExpl;
  }

}
