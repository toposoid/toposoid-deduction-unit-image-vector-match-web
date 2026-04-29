/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import com.ideal.linked.toposoid.common.{SentenceType, ScopeType, FeatureType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseEdge, KnowledgeBaseNode}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, CoveredPropositionEdge, CoveredPropositionNode, KnowledgeBaseSideInfo, MatchedFeatureInfo}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{Json, __}

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json.JsValue

import scala.util.{Failure, Success, Try}
import com.ideal.linked.toposoid.protocol.model.base.DeductionResult
import com.ideal.linked.toposoid.common.Neo4JUtilsImpl
import com.ideal.linked.toposoid.protocol.model.base.VerifyingEdges
import com.ideal.linked.toposoid.common.DeductionUtils
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import com.ideal.linked.toposoid.common.RelationMatchState
import com.ideal.linked.toposoid.protocol.model.base.MatchedKnowledgeNode
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeFeatureReference
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.DeductionQuery

//case class DeductionQuery(query:String,relationMatchState:RelationMatchState, sourceAlias:String, destinationAlias:String,isSourceConfirmed:Boolean, isDestinationConfirmed:Boolean, featureSimilarityMap:Map[String, Float] = Map.empty[String, Float])

class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController /*with DeductionUnitController*/ with LazyLogging {
  def execute():Action[JsValue] = Action(parse.json[JsValue])  { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos: List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects
      //Check if the image exists on asos here　or not.
      if (getAnalyzedSentenceObjectsWithImage(asos).size > 0) {

        val result:List[VerifyingEdges] = asos.foldLeft(List.empty[VerifyingEdges]){
          (acc, aso) => { 
            acc :+ VerifyingEdges(
              propositionId = aso.knowledgeBaseSemiGlobalNode.propositionId,
              sentenceId = aso.knowledgeBaseSemiGlobalNode.sentenceId,
              coveredPropositionEdges = DeductionUtils.analyzeGraphKnowledge(getQeuries, aso, transversalState)
            )
          }
        }
        logger.info(ToposoidUtils.formatMessageForLogger("Image edge analysis completed.", transversalState.userId))    
        Ok(Json.toJson(result)).as(JSON)  
      } else {
        logger.info(ToposoidUtils.formatMessageForLogger("deduction skipped[No Images].", transversalState.userId))
        Ok(Json.toJson(List.empty[VerifyingEdges])).as(JSON) 
      }    
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  private def getQeuries(edge:KnowledgeBaseEdge, nodeMap:Map[String, KnowledgeBaseNode], transversalState:TransversalState):List[DeductionQuery] = {
        
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNode = nodeMap.get(sourceKey).get.asInstanceOf[KnowledgeBaseNode]
    val destinationNode = nodeMap.get(targetKey).get.asInstanceOf[KnowledgeBaseNode]
    val nodeType: String = ToposoidUtils.getNodeType(SentenceType.CLAIM.index, ScopeType.LOCAL.index, FeatureType.PREDICATE_ARGUMENT.index)
    val sourceFeatureSimilarityMap = getSimilarImage(sourceNode, SentenceType.CLAIM.index, transversalState) 
    val destinationFeatureSimilarityMap = getSimilarImage(destinationNode, SentenceType.CLAIM.index, transversalState) 
    val totalFeatureSimilarityMap = sourceFeatureSimilarityMap ++ destinationFeatureSimilarityMap

    //SourceSideがすでにOKの場合  
    val query1 = "MATCH (n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1, e, n2ext".format(nodeType, nodeType, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))
    //DestinationSideがすでにOKの場合
    val query2 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' RETURN n1ext, e, n2".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.surface)
    //両サイドともOKでない場合かつ、両サイド結果としてOKになる場合
    val query3 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]->(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1ext, e, n2ext".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))
    //両サイドともOKでない場合かつ、Sourceのみ結果としてOKになる場合
    val query4 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' RETURN n1ext, e, n2".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord)
    //両サイドともOKでない場合かつ、Destinationのみ結果としてOKになる場合
    val query5 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1, e, n2ext".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))

    val existFeatureOnSource = sourceNode.localContext.knowledgeFeatureReferences.filter(x => List(FeatureType.IMAGE.index, FeatureType.TABLE.index).contains(x.featureType)).size > 0 && sourceFeatureSimilarityMap.size > 0
    val existFeatureOnDestination = destinationNode.localContext.knowledgeFeatureReferences.filter(x => List(FeatureType.IMAGE.index, FeatureType.TABLE.index).contains(x.featureType)).size > 0 && destinationFeatureSimilarityMap.size > 0

    //命題のFeatureNodeのペアをどう持つかで、仮に表層テキスト単位でマッチしても判断を先送りする必要がある。RelationMatchStateを指定している意味。
    (existFeatureOnSource, existFeatureOnDestination) match
      case (false, false) => {
        List.empty[DeductionQuery]
      }
      case (true, true) => {
        List(
          DeductionQuery(query1, RelationMatchState.MATCHED_BOTH, "n1", "n2ext", true, false, totalFeatureSimilarityMap),
          DeductionQuery(query2, RelationMatchState.MATCHED_BOTH, "n1ext", "n2", false, true, totalFeatureSimilarityMap),
          DeductionQuery(query3, RelationMatchState.MATCHED_BOTH, "n1ext", "n2ext", false, false, totalFeatureSimilarityMap),
          DeductionQuery(query4, RelationMatchState.MATCHED_SOURCE_NODE_ONLY, "n1ext", "n2", false, false, totalFeatureSimilarityMap),
          DeductionQuery(query5, RelationMatchState.MATCHED_TARGET_NODE_ONLY, "n1", "n2ext", false, false, totalFeatureSimilarityMap),
        )      
      }
      case (true, false) => {
        List(
          DeductionQuery(query2, RelationMatchState.MATCHED_BOTH, "n1ext", "n2", false, true, totalFeatureSimilarityMap),
          DeductionQuery(query4, RelationMatchState.MATCHED_SOURCE_NODE_ONLY, "n1ext", "n2", false, false, totalFeatureSimilarityMap),
        )      
      }
      case (false, true) => {
        List(
          DeductionQuery(query1, RelationMatchState.MATCHED_BOTH, "n1", "n2ext", true, false, totalFeatureSimilarityMap),
          DeductionQuery(query5, RelationMatchState.MATCHED_TARGET_NODE_ONLY, "n1", "n2ext", false, false, totalFeatureSimilarityMap),
        )      
      }
  }
  /*
  private def analyzeGraphKnowledge(getQeuries:(KnowledgeBaseEdge, Map[String, KnowledgeBaseNode], TransversalState) => List[DeductionQuery], edges: List[KnowledgeBaseEdge], aso:AnalyzedSentenceObject, transversalState:TransversalState):List[CoveredPropositionEdge] = {    
    val futures: List[Future[Option[CoveredPropositionEdge]]] = edges.foldLeft(List.empty[Future[Option[CoveredPropositionEdge]]]){
      (acc, edge) => {
        val deductionQueries = getQeuries(edge, aso.nodeMap, transversalState)       
        deductionQueries.size match {
          case 0 => acc :+ Future(Option(aso.deductionResult.coveredPropositionEdges.filter(x => x.sourceNode.terminalId.equals(edge.sourceId) && x.destinationNode.terminalId.equals(edge.destinationId)).head))
          case _ => acc :+ Future(analyzeEdge(0, deductionQueries, edge, aso, Neo4JUtilsImpl(), transversalState))
        }        
      }
    }    
    val combinedFuture: Future[List[Option[CoveredPropositionEdge]]] = Future.sequence(futures)
    val result = Await.result(combinedFuture, Duration.Inf)    
    result.flatten
  }

  private def analyzeEdge(idx:Int, deductionQueries:List[DeductionQuery],edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState):Option[CoveredPropositionEdge] = {
    val nodeMap = aso.nodeMap
    val sourceNode = nodeMap.get(edge.sourceId).get.asInstanceOf[KnowledgeBaseNode]
    val destinationNode = nodeMap.get(edge.destinationId).get.asInstanceOf[KnowledgeBaseNode]

    //引数のisSourceConfirmed, isDestinationConfirmedとdeductionQueries(idx)のisSourceConfirmed, isDestinationConfirmedが同じかをチェックする。
    //チェックNGの場合は、analyzeEdge(idx+1, deductionQueries, edge, nodeMap, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)    
    val coveredPropositionEdges = aso.deductionResult.coveredPropositionEdges.filter(x => {
      x.sourceNode.terminalId.equals(edge.sourceId) && x.destinationNode.terminalId.equals(edge.destinationId)
    })
    val (isSourceConfirmed, isDestinationConfirmed, coveredPropositionEdge) = coveredPropositionEdges.size match {
      case 0 => (false, false, None) //BaseMatch用
      case _ => (coveredPropositionEdges.head.sourceNode.isConfirmed, coveredPropositionEdges.head.destinationNode.isConfirmed, Option(coveredPropositionEdges.head))
    }
    //クエリを実行する必要のない場合は、早めに判断し次のクエリを実行を促す。
    if(!deductionQueries(idx).isSourceConfirmed == isSourceConfirmed || !deductionQueries(idx).isDestinationConfirmed == isDestinationConfirmed){
      if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
      else coveredPropositionEdge  
    }else{

      val sourceMorphemes = sourceNode.predicateArgumentStructure.morphemes
      val destinationMorphemes = destinationNode.predicateArgumentStructure.morphemes
      val isVerbOrNounOnSource = sourceNode.localContext.lang match {
        case "ja_JP" =>  sourceMorphemes.filter(x => x.split(",").toList.contains("動詞")).size > 0 || sourceMorphemes.filter(x => x.split(",").toList.contains("名詞")).size > 0
        case "en_US" => sourceMorphemes.filter(x => x.split(",").toList.contains("VERB")).size > 0  || sourceMorphemes.filter(x => x.split(",").toList.contains("NOUN")).size > 0
      }
      val isVerbOrNounOnDestination = destinationNode.localContext.lang match {
        case "ja_JP" =>  destinationMorphemes.filter(x => x.split(",").toList.contains("動詞")).size > 0 || destinationMorphemes.filter(x => x.split(",").toList.contains("名詞")).size > 0
        case "en_US" => destinationMorphemes.filter(x => x.split(",").toList.contains("VERB")).size > 0  || destinationMorphemes.filter(x => x.split(",").toList.contains("NOUN")).size > 0
      }

      deductionQueries(idx).relationMatchState match {
        case RelationMatchState.MATCHED_BOTH => {        
          analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, transversalState) match {
            case Some(x) => Option(x)
            case _ => {
              if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
              else coveredPropositionEdge
            }}
        }
        case RelationMatchState.MATCHED_SOURCE_NODE_ONLY => {
          if(isVerbOrNounOnDestination){
            analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, transversalState) match {
              case Some(x) => Option(x)
              case _ => {
                if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
                else coveredPropositionEdge  
              }}          
          }else {
            if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
            else coveredPropositionEdge        
          }
        }
        case RelationMatchState.MATCHED_TARGET_NODE_ONLY => {
          if(isVerbOrNounOnSource) {
            analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, transversalState) match {
              case Some(x) => Option(x)
              case _ => {
                if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
                else coveredPropositionEdge  
              }}          
          }else {
            if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
            else coveredPropositionEdge        
          }
        }
        case RelationMatchState.NOT_MATCHED_BOTH => {
          if(isVerbOrNounOnSource && isVerbOrNounOnDestination){
            analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, transversalState) match {
              case Some(x) => Option(x)
              case _ => {
                if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
                else coveredPropositionEdge  
              }}          
          }else {
            if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState)
            else coveredPropositionEdge        
          }
        }
      }
    }
  }
  
  private def analyze(idx:Int, deductionQueries:List[DeductionQuery],edge:KnowledgeBaseEdge, nodeMap: Map[String, KnowledgeBaseNode], neo4JUtils:Neo4JUtilsImpl, transversalState:TransversalState):Option[CoveredPropositionEdge] = {
    val deductionUnitName = conf.getString("TOPOSOID_DEDUCTION_UNIT_NAME")
    val jsonStr: String = neo4JUtils.getCypherQueryResult(deductionQueries(idx).query, "", transversalState)
    //If there is even one that does not match, it is useless to search further
    if (!jsonStr.equals("""{"records":[]}""")) {
      //ヒットするものがある場合
      val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]      
      Option(DeductionUtils.getCoveredPropositionEdge(edge, deductionQueries(idx).sourceAlias, deductionQueries(idx).destinationAlias, nodeMap,  neo4jRecords, deductionQueries(idx).relationMatchState, deductionUnitName, deductionQueries(idx).featureSimilarityMap))        
    }else{
      None
    }
  }
  */
  
  /*
  //Synonymで埋められてる場合も考慮
  
  private def analyzeEdge(edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, transversalState:TransversalState):Option[CoveredPropositionEdge] = {
    val nodeMap: Map[String, KnowledgeBaseNode] =  aso.nodeMap    
    val deductionResult:DeductionResult = aso.deductionResult
    val neo4JUtils = Neo4JUtilsImpl()
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNode = nodeMap.get(sourceKey).get.asInstanceOf[KnowledgeBaseNode]
    val destinationNode = nodeMap.get(targetKey).get.asInstanceOf[KnowledgeBaseNode]
    val deductionUnitName = conf.getString("TOPOSOID_DEDUCTION_UNIT_NAME")

    //sentenceIdも絞り込めるがどうするか？  
    val coveredPropositionEdges = aso.deductionResult.coveredPropositionEdges.filter(x => {
      x.sourceNode.terminalId.equals(sourceKey) && x.destinationNode.terminalId.equals(targetKey)
    })

    if(coveredPropositionEdges.size == 0) {
      None
    }else{
      val coveredPropositionEdge = coveredPropositionEdges.head
      val nodeType: String = ToposoidUtils.getNodeType(SentenceType.CLAIM.index, ScopeType.LOCAL.index, FeatureType.PREDICATE_ARGUMENT.index)
      //PremiseのSentenceTypeが何故必要？ → 一旦、Premiseなしでやってみる。
      if(coveredPropositionEdge.sourceNode.isConfirmed && !coveredPropositionEdge.destinationNode.isConfirmed){
        val sourceAlias = "n1"
        val destinationAlias = "n2ext"
        val featureSimilarityMap = getSimilarImage(destinationNode, SentenceType.CLAIM.index, transversalState) 
        featureSimilarityMap.size match {
          case 0 => Option(coveredPropositionEdge)
          case _ => {
            val querySourceOnly = "MATCH (n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1.surface=\"%s\" AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1, e, n2ext".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.surface, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(featureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))        
            logger.debug(querySourceOnly)
            val jsonStr: String = neo4JUtils.getCypherQueryResult(querySourceOnly, "", transversalState)
            if (!jsonStr.equals("""{"records":[]}""")) {
              //ヒットするものがある場合
              val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]              
              Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH, deductionUnitName, featureSimilarityMap))     
            }else{
              Option(coveredPropositionEdge)
            }        
          }
        }
      }else if(!coveredPropositionEdge.sourceNode.isConfirmed && coveredPropositionEdge.destinationNode.isConfirmed){
        val sourceAlias = "n1ext"
        val destinationAlias = "n2"
        val featureSimilarityMap = getSimilarImage(sourceNode, SentenceType.CLAIM.index, transversalState) 
        featureSimilarityMap.size match {
          case 0 => Option(coveredPropositionEdge)
          case _ => {
            val queryTargetOnly = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.surface=\"%s\" RETURN n1ext, e, n2".format(nodeType, nodeType, "[%s]".format(featureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.surface)
            logger.debug(queryTargetOnly)
            val jsonStr: String = neo4JUtils.getCypherQueryResult(queryTargetOnly, "", transversalState)
            if (!jsonStr.equals("""{"records":[]}""")) {
              //ヒットするものがある場合
              val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]              
              Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH, deductionUnitName, featureSimilarityMap))     
            }else{
              Option(coveredPropositionEdge)
            }          
          }
        }
      }else if(!coveredPropositionEdge.sourceNode.isConfirmed && !coveredPropositionEdge.destinationNode.isConfirmed){

        val sourceFeatureSimilarityMap = getSimilarImage(sourceNode, SentenceType.CLAIM.index, transversalState) 
        val destinationFeatureSimilarityMap = getSimilarImage(destinationNode, SentenceType.CLAIM.index, transversalState) 
        //val queryBothReplacement = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]->(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1ext, e, n2ext".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))
        
        sourceFeatureSimilarityMap.size + destinationFeatureSimilarityMap.size match {
          case 0 => Option(coveredPropositionEdge)
          case _ => {
            val queryBothReplacement = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]->(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1ext, e, n2ext".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))
            logger.debug(queryBothReplacement)
            val jsonStr: String = neo4JUtils.getCypherQueryResult(queryBothReplacement, "", transversalState)
            //If there is even one that does not match, it is useless to search further
            if (!jsonStr.equals("""{"records":[]}""")) {
              //ヒットするものがある場合
              val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
              //Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH))     
              Option(DeductionUtils.getCoveredPropositionEdge(edge, "n1ext", "n2ext", nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH, deductionUnitName, sourceFeatureSimilarityMap++destinationFeatureSimilarityMap))     
            }else{
              val queryBothReplacement = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' RETURN n1ext, e, n2".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord)
              val jsonStr: String = neo4JUtils.getCypherQueryResult(queryBothReplacement, "", transversalState)
              //If there is even one that does not match, it is useless to search further
              if (!jsonStr.equals("""{"records":[]}""")) {
                //ヒットするものがある場合
                val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
                //Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH))     
                Option(DeductionUtils.getCoveredPropositionEdge(edge, "n1ext", "n2", nodeMap,  neo4jRecords, RelationMatchState.MATCHED_SOURCE_NODE_ONLY, deductionUnitName, sourceFeatureSimilarityMap++destinationFeatureSimilarityMap))     
              }else{
                val queryBothReplacement = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1, e, n2ext".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")))
                val jsonStr: String = neo4JUtils.getCypherQueryResult(queryBothReplacement, "", transversalState)
                //If there is even one that does not match, it is useless to search further
                if (!jsonStr.equals("""{"records":[]}""")) {
                  //ヒットするものがある場合
                  val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
                  //Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH))     
                  Option(DeductionUtils.getCoveredPropositionEdge(edge, "n1", "n2ext", nodeMap,  neo4jRecords, RelationMatchState.MATCHED_TARGET_NODE_ONLY, deductionUnitName, sourceFeatureSimilarityMap++destinationFeatureSimilarityMap))     
                }else{
                  Option(coveredPropositionEdge) 
                }
              }
            }        
          }
        }
        
        /*
        val (queryBothReplacement,relationMatchState, sourceAlias, destinationAlias) = sourceFeatureSimilarityMap.size + destinationFeatureSimilarityMap.size match {
          case 0 => ("", RelationMatchState.NOT_MATCHED_BOTH, "", "")
          case _ => {
            if(sourceFeatureSimilarityMap.size > 0){
              if(destinationFeatureSimilarityMap.size > 0){
                ("MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]->(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1ext, e, n2ext".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(","))), RelationMatchState.MATCHED_BOTH, "n1ext", "n2ext")
              }else{
                ("MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1ext.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' RETURN n1ext, e, n2".format(nodeType, nodeType, "[%s]".format(sourceFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord), RelationMatchState.MATCHED_SOURCE_NODE_ONLY, "n1ext", "n2")
              }
            }else {
              ("MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2ext.featureId IN %s RETURN n1, e, n2ext".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.isDenialWord, edge.caseStr, destinationNode.predicateArgumentStructure.isDenialWord, "[%s]".format(destinationFeatureSimilarityMap.keys.map("'%s'".format(_)).mkString(","))), RelationMatchState.MATCHED_TARGET_NODE_ONLY, "n1", "n2ext")        
            }
          }
        }          
        if(queryBothReplacement.equals("")){
          Option(coveredPropositionEdge)        
        }else{
          logger.debug(queryBothReplacement)
          val jsonStr: String = neo4JUtils.getCypherQueryResult(queryBothReplacement, "", transversalState)
          //If there is even one that does not match, it is useless to search further
          if (!jsonStr.equals("""{"records":[]}""")) {
            //ヒットするものがある場合
            val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
            //Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, RelationMatchState.MATCHED_BOTH))     
            Option(DeductionUtils.getCoveredPropositionEdge(edge, sourceAlias, destinationAlias, nodeMap,  neo4jRecords, relationMatchState, deductionUnitName, sourceFeatureSimilarityMap++destinationFeatureSimilarityMap))     
          }else{
            Option(coveredPropositionEdge)
          }        
        } */
        //Option(coveredPropositionEdge)
      }else{
        Option(coveredPropositionEdge)
      }
    }
  }

  private def analyzeGraphKnowledge(edges: List[KnowledgeBaseEdge], aso:AnalyzedSentenceObject, transversalState:TransversalState):List[CoveredPropositionEdge] = {
    
    val futures: List[Future[Option[CoveredPropositionEdge]]] = edges.foldLeft(List.empty[Future[Option[CoveredPropositionEdge]]]){
      (acc, edge) => {
        acc :+ Future(analyzeEdge(edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, transversalState))
      }
    }    
    val combinedFuture: Future[List[Option[CoveredPropositionEdge]]] = Future.sequence(futures)
    val result = Await.result(combinedFuture, Duration.Inf)    
    result.flatten
  }
  */
  
  private def getAnalyzedSentenceObjectsWithImage(asos: List[AnalyzedSentenceObject]): List[AnalyzedSentenceObject] = {
    asos.filter(x => {
      x.nodeMap.filter(y => {
        y._2.localContext.knowledgeFeatureReferences.filter(z => {
          z.featureType == FeatureType.IMAGE.index
        }).size > 0
      }).size > 0
    })
  }

  private def getSimilarImage(node:KnowledgeBaseNode,sentenceType:Int, transversalState:TransversalState):Map[String, Float] = {
    //There may be multiple image nodes, so check them all
    node.localContext.knowledgeFeatureReferences.foldLeft(Map.empty[String, Float]){(acc, x) => {

      val vector = FeatureVectorizer.getImageVector(x.url, transversalState)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      val result:FeatureVectorSearchResult = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      acc ++ result.ids.zip(result.similarities).filter(y => y._1.sentenceType == sentenceType).map( z => ( z._1.featureId -> z._2))
    }}
  }
}