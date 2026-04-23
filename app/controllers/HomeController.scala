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

/*
sealed abstract class RelationMatchState(val index: Int)
final case object MATCHED_SOURCE_NODE_ONLY extends RelationMatchState(0)
final case object MATCHED_TARGET_NODE_ONLY extends RelationMatchState(1)
final case object NOT_MATCHED extends RelationMatchState(2)
*/

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
              coveredPropositionEdges = analyzeGraphKnowledge(DeductionUtils.getUnsettledEdges(aso), aso, transversalState)
            )
          }
        }
        logger.info(ToposoidUtils.formatMessageForLogger("Image edge analysis completed.", transversalState.userId))    
        Ok(Json.toJson(result)).as(JSON)  
      } else {
        logger.info(ToposoidUtils.formatMessageForLogger("deduction skipped[No Images].", transversalState.userId))
        Ok(Json.toJson(analyzedSentenceObjects)).as(JSON) 
      }    
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

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
        }  
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