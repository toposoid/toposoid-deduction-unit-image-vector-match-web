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
import com.ideal.linked.toposoid.knowledgebase.image.model.SingleImage


class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with LazyLogging {
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

  private def getQeuries(edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, transversalState:TransversalState):List[DeductionQuery] = {
    
    val sentenceIds = aso.deductionResult.coveredPropositionEdges.foldLeft(List.empty[String]){
      (acc, x) =>
        acc ++ x.sourceNode.matchedKnowledgeNodes.map(y => "'" + y.sentenceId + "'")
    }.distinct

    val sentenceIdFilterQuery = sentenceIds.size match {
      case 0 => ""
      case _ => "AND n1.sentenceId IN [%s]".format(sentenceIds.mkString(","))
    }
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNode = aso.nodeMap.get(sourceKey).get.asInstanceOf[KnowledgeBaseNode]
    val destinationNode = aso.nodeMap.get(targetKey).get.asInstanceOf[KnowledgeBaseNode]
    val nodeType: String = ToposoidUtils.getNodeType(SentenceType.CLAIM.index, ScopeType.LOCAL.index, FeatureType.PREDICATE_ARGUMENT.index)

    val sourceConfirmedNode = aso.deductionResult.coveredPropositionEdges.filter(x => x.sourceNode.isConfirmed && x.sourceNode.terminalId.equals(sourceKey))
    val destinationConfirmedNode = aso.deductionResult.coveredPropositionEdges.filter(x => x.destinationNode.isConfirmed && x.destinationNode.terminalId.equals(targetKey))        
    val sourceConfirmedSentenceIds = sourceConfirmedNode.size match {
      case 0 => List.empty[String]
      case _ =>  sourceConfirmedNode.head.sourceNode.matchedKnowledgeNodes.map(y => "'" + y.sentenceId + "'").distinct
    }
    val destinationConfirmedSentenceIds = destinationConfirmedNode.size match {
      case 0 => List.empty[String]
      case _ =>  destinationConfirmedNode.head.destinationNode.matchedKnowledgeNodes.map(y => "'" + y.sentenceId + "'").distinct
    }
      
    val sourceConfirmedQuery = sourceConfirmedSentenceIds.size match {
      case 0 => ""
      case _ => "AND n1.sentenceId IN [%s]".format(sourceConfirmedSentenceIds.mkString(","))
    }
    val destinationConfirmedQuery = destinationConfirmedSentenceIds.size match {
      case 0 => ""
      case _ => "AND n2.sentenceId IN [%s]".format(destinationConfirmedSentenceIds.mkString(","))
    }

    val sourceFeatureSimilarityMap = getSimilarImage(sourceNode, SentenceType.CLAIM.index, transversalState) 
    val destinationFeatureSimilarityMap = getSimilarImage(destinationNode, SentenceType.CLAIM.index, transversalState) 

    val totalFeatureSimilarityMap = sourceFeatureSimilarityMap ++ destinationFeatureSimilarityMap

    val sourceFeatureFilterQuery = sourceFeatureSimilarityMap.size match {
      case 0 => "n1ext.featureId='-' AND " //この場合はマッチしない状況を設定      
      case _ => {
        val queries = sourceFeatureSimilarityMap.foldLeft(List.empty[String]){
          (acc, x) =>{
            acc :+ "n1ext.featureId='%s'".format(x._1)
          }
        }
        queries.size match {
          case 0 => ""
          case _ => "(" + queries.mkString(" OR ") + ") AND "
        }
      }      
    }

    val destinationFeatureFilterQuery = destinationFeatureSimilarityMap.size match {
      case 0 => "n2ext.featureId='-' AND " //この場合はマッチしない状況を設定      
      case _ => {
        val queries = destinationFeatureSimilarityMap.foldLeft(List.empty[String]){
          (acc, x) =>{
            acc :+ "n2ext.featureId='%s'".format(x._1)
          }
        }
        queries.size match {
          case 0 => ""
          case _ => "(" + queries.mkString(" OR ") + ") AND "
        }
      }
    }
      
    val totalFeatureQuery  = sourceFeatureFilterQuery + destinationFeatureFilterQuery match {
      case "" => ""
      case _ => {
        if(sourceFeatureFilterQuery.length() > 0 && destinationFeatureFilterQuery.length() > 0) sourceFeatureFilterQuery + destinationFeatureFilterQuery
        else if(sourceFeatureFilterQuery.length() > 0 && destinationFeatureFilterQuery.length() == 0) sourceFeatureFilterQuery
        else if(sourceFeatureFilterQuery.length() == 0 && destinationFeatureFilterQuery.length() > 0) destinationFeatureFilterQuery 
        else ""
      } 
    }

    val sourcePas = sourceNode.predicateArgumentStructure
    val destinationPas = destinationNode.predicateArgumentStructure

    //SourceSideがすでにOKの場合  
    val query1 = "MATCH (n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE %s e.caseName='%s' AND n2.isDenialWord='%s' AND n2.modalityType='%s' %s RETURN n1, e, n2ext".format(nodeType, nodeType, destinationFeatureFilterQuery, edge.caseStr, destinationPas.isDenialWord, destinationPas.modalityType, sourceConfirmedQuery)
    //DestinationSideがすでにOKの場合
    val query2 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s) WHERE %s n1.isDenialWord='%s' AND n1.modalityType='%s' AND e.caseName='%s' %s RETURN n1ext, e, n2".format(nodeType, nodeType, sourceFeatureFilterQuery, sourcePas.isDenialWord, sourcePas.modalityType, edge.caseStr, destinationConfirmedQuery)
    //両サイドともOKでない場合かつ、両サイド結果としてOKになる場合
    val query3 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE %s n1.isDenialWord='%s' AND n1.modalityType='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2.modalityType='%s' %s RETURN n1ext, e, n2ext".format(nodeType, nodeType, totalFeatureQuery, sourcePas.isDenialWord, sourcePas.modalityType, edge.caseStr, destinationPas.isDenialWord, destinationPas.modalityType, sentenceIdFilterQuery)
    //両サイドともOKでない場合かつ、Sourceのみ結果としてOKになる場合
    val query4 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE %s n1.isDenialWord='%s' AND n1.modalityType='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2.modalityType='%s' %s RETURN n1ext, e, n2".format(nodeType, nodeType, sourceFeatureFilterQuery, sourcePas.isDenialWord, sourcePas.modalityType, edge.caseStr, destinationPas.isDenialWord, destinationPas.modalityType, sentenceIdFilterQuery)
    //両サイドともOKでない場合かつ、Destinationのみ結果としてOKになる場合
    val query5 = "MATCH (n1ext:ImageNode)-[e1ext:ImageEdge]-(n1:%s)-[e]->(n2:%s)-[e2ext:ImageEdge]-(n2ext:ImageNode) WHERE %s n1.isDenialWord='%s' AND n1.modalityType='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND n2.modalityType='%s' %s RETURN n1, e, n2ext".format(nodeType, nodeType, destinationFeatureFilterQuery, sourcePas.isDenialWord, sourcePas.modalityType, edge.caseStr, destinationPas.isDenialWord, destinationPas.modalityType, sentenceIdFilterQuery)

    val existFeatureOnSource = sourceNode.localContext.knowledgeFeatureReferences.filter(x => FeatureType.IMAGE.index == x.featureType).size > 0 && sourceFeatureSimilarityMap.size > 0
    val existFeatureOnDestination = destinationNode.localContext.knowledgeFeatureReferences.filter(x => FeatureType.IMAGE.index == x.featureType).size > 0 && destinationFeatureSimilarityMap.size > 0

    //命題のFeatureNodeのペアをどう持つかで、仮に表層テキスト単位でマッチしても判断を先送りする必要がある。RelationMatchStateを指定している意味。
    (existFeatureOnSource, existFeatureOnDestination) match
      case (false, false) => {
        logger.info(ToposoidUtils.formatMessageForLogger("query is nothing.", transversalState.userId))  
        List.empty[DeductionQuery]        
      }
      case (true, true) => {
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query1:$query1", transversalState.userId))  
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query2:$query2", transversalState.userId))  
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query3:$query3", transversalState.userId))  
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query4:$query4", transversalState.userId))  
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query5:$query5", transversalState.userId))  
        List(
          DeductionQuery(query1, RelationMatchState.MATCHED_BOTH, "n1", "n2ext", true, false, totalFeatureSimilarityMap),
          DeductionQuery(query2, RelationMatchState.MATCHED_BOTH, "n1ext", "n2", false, true, totalFeatureSimilarityMap),
          DeductionQuery(query3, RelationMatchState.MATCHED_BOTH, "n1ext", "n2ext", false, false, totalFeatureSimilarityMap),
          DeductionQuery(query4, RelationMatchState.MATCHED_SOURCE_NODE_ONLY, "n1ext", "n2", false, false, totalFeatureSimilarityMap),
          DeductionQuery(query5, RelationMatchState.MATCHED_TARGET_NODE_ONLY, "n1", "n2ext", false, false, totalFeatureSimilarityMap),
        )    
      }
      case (true, false) => {
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query2:$query2", transversalState.userId))  
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query4:$query4", transversalState.userId))  
        List(
          DeductionQuery(query2, RelationMatchState.MATCHED_BOTH, "n1ext", "n2", false, true, totalFeatureSimilarityMap),
          DeductionQuery(query4, RelationMatchState.MATCHED_SOURCE_NODE_ONLY, "n1ext", "n2", false, false, totalFeatureSimilarityMap),
        )      
      }
      case (false, true) => {
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query1:$query1", transversalState.userId))  
        //logger.info(ToposoidUtils.formatMessageForLogger(s"query5:$query5", transversalState.userId))  
        List(
          DeductionQuery(query1, RelationMatchState.MATCHED_BOTH, "n1", "n2ext", true, false, totalFeatureSimilarityMap),
          DeductionQuery(query5, RelationMatchState.MATCHED_TARGET_NODE_ONLY, "n1", "n2ext", false, false, totalFeatureSimilarityMap),
        )      
      }
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

      val vector = FeatureVectorizer.getImageVector(SingleImage(url=x.url), transversalState)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      val result:FeatureVectorSearchResult = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      acc ++ result.ids.zip(result.similarities).filter(y => y._1.sentenceType == sentenceType).map( z => ( z._1.featureId  -> z._2))
    }}
  }
}


