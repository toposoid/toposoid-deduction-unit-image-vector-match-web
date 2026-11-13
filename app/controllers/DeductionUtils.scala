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

package com.ideal.linked.toposoid.common

import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObject
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseEdge
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseNode
import scala.util.{Failure, Success, Try}
import com.ideal.linked.common.DeploymentConverter.conf
import com.typesafe.scalalogging.LazyLogging
import com.ideal.linked.toposoid.protocol.model.base.MatchedKnowledgeNode
import com.ideal.linked.toposoid.protocol.model.base.MatchedFeatureInfo
import com.ideal.linked.toposoid.protocol.model.base.KnowledgeBaseSideInfo
import com.ideal.linked.toposoid.protocol.model.base.CoveredPropositionNode
import com.ideal.linked.toposoid.protocol.model.base.CoveredPropositionEdge
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseSynonymNode
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeFeatureReference
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import play.api.libs.json.{Json, __}


case class DeductionQuery(query:String,relationMatchState:RelationMatchState, sourceAlias:String, destinationAlias:String,isSourceConfirmed:Boolean, isDestinationConfirmed:Boolean, featureSimilarityMap:Map[String, Float] = Map.empty[String, Float])

object DeductionUtils extends LazyLogging {

    def analyzeGraphKnowledge(getQeuries:(KnowledgeBaseEdge, AnalyzedSentenceObject, TransversalState) => List[DeductionQuery], aso:AnalyzedSentenceObject, transversalState:TransversalState):List[CoveredPropositionEdge] = {    
        val edges:List[KnowledgeBaseEdge] = getUnsettledEdges(aso)

        //既に前のプロセスで否定表現が確認できている場合はその結果を利用するために取得。
        val isNegativeMeaningMap:Map[String,Boolean] = aso.deductionResult.evidenceKnowledgeList.size match {
            case 0 => Map.empty[String, Boolean]
            case _ => aso.deductionResult.evidenceKnowledgeList.map(x => (x.sentenceId -> x.isNegativeMeaning)).toMap[String, Boolean]
        }                

        val futures: List[Future[Option[CoveredPropositionEdge]]] = edges.foldLeft(List.empty[Future[Option[CoveredPropositionEdge]]]){
            (acc, edge) => {
                val deductionQueries = getQeuries(edge, aso, transversalState)       
                deductionQueries.size match {
                    case 0 => {
                        aso.deductionResult.coveredPropositionEdges.size match {
                            case 0 => acc
                            case _ => {
                                val filteredCoveredPropositionEdges = aso.deductionResult.coveredPropositionEdges.filter(x => x.sourceNode.terminalId.equals(edge.sourceId) && x.destinationNode.terminalId.equals(edge.destinationId))
                                filteredCoveredPropositionEdges.size match {
                                    case 0 => acc
                                    case _ => acc :+ Future(Option(filteredCoveredPropositionEdges.head))
                                }                                
                            }
                        }                
                    }
                    case _ => acc :+ Future(analyzeEdge(0, deductionQueries, edge, aso, Neo4JUtilsImpl(), isNegativeMeaningMap, transversalState))
                }        
            }
        }         
        val combinedFuture: Future[List[Option[CoveredPropositionEdge]]] = Future.sequence(futures)
        val result = Await.result(combinedFuture, Duration.Inf)    
        result.flatten
    }
    private def getUnsettledEdges(aso:AnalyzedSentenceObject): List[KnowledgeBaseEdge] = {
        //TODO:ロジカルエッヂを省けてる？
        val pairSetList = aso.deductionResult.coveredPropositionEdges.foldLeft(List.empty[Set[String]]){
            (acc, x) => {
                if(x.sourceNode.isConfirmed && x.destinationNode.isConfirmed){
                    acc :+ Set(x.sourceNode.terminalId, x.destinationNode.terminalId)
                }else{
                    acc
                }        
            }
        }
        aso.edgeList.filterNot(x => {
            val targetLink = Set(x.sourceId, x.destinationId)
            pairSetList.contains(targetLink)
        })    
    }

    private def analyzeEdge(idx:Int, deductionQueries:List[DeductionQuery],edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap:Map[String,Boolean], transversalState:TransversalState):Option[CoveredPropositionEdge] = {
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
            if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
            else coveredPropositionEdge  
        }else{

            val sourceMorphemes = sourceNode.predicateArgumentStructure.morphemes
            val destinationMorphemes = destinationNode.predicateArgumentStructure.morphemes
            val isVerbOrNounOnSource = sourceNode.localContext.lang match {
                case "ja_JP" =>  sourceMorphemes.filter(x => x.split(",").toList.contains("動詞")).size > 0 || sourceMorphemes.filter(x => x.split(",").toList.contains("名詞")).size > 0
                case "en_US" => sourceMorphemes.filter(x => x.split(",").toList.contains("VERB")).size > 0  || sourceMorphemes.filter(x => x.split(",").toList.contains("NOUN")).size > 0 || sourceMorphemes.filter(x => x.split(",").toList.contains("PROPN")).size > 0
            }
            val isVerbOrNounOnDestination = destinationNode.localContext.lang match {
                case "ja_JP" =>  destinationMorphemes.filter(x => x.split(",").toList.contains("動詞")).size > 0 || destinationMorphemes.filter(x => x.split(",").toList.contains("名詞")).size > 0
                case "en_US" => destinationMorphemes.filter(x => x.split(",").toList.contains("VERB")).size > 0  || destinationMorphemes.filter(x => x.split(",").toList.contains("NOUN")).size > 0 || destinationMorphemes.filter(x => x.split(",").toList.contains("PROPN")).size > 0
            }

            deductionQueries(idx).relationMatchState match {
                case RelationMatchState.MATCHED_BOTH => {        
                    analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, isNegativeMeaningMap, transversalState) match {
                    case Some(x) => Option(x)
                    case _ => {
                        if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                        else coveredPropositionEdge
                    }}
                }
                case RelationMatchState.MATCHED_SOURCE_NODE_ONLY => {
                    if(isVerbOrNounOnDestination){
                    analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, isNegativeMeaningMap, transversalState) match {
                        case Some(x) => Option(x)
                        case _ => {
                        if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                        else coveredPropositionEdge  
                        }}          
                    }else {
                    if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                    else coveredPropositionEdge        
                    }
                }
                case RelationMatchState.MATCHED_TARGET_NODE_ONLY => {
                    if(isVerbOrNounOnSource) {
                    analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, isNegativeMeaningMap, transversalState) match {
                        case Some(x) => Option(x)
                        case _ => {
                        if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                        else coveredPropositionEdge  
                        }}          
                    }else {
                    if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                    else coveredPropositionEdge        
                    }
                }
                case RelationMatchState.NOT_MATCHED_BOTH => {
                    if(isVerbOrNounOnSource && isVerbOrNounOnDestination){
                    analyze(idx, deductionQueries, edge, nodeMap, neo4JUtils, isNegativeMeaningMap, transversalState) match {
                        case Some(x) => Option(x)
                        case _ => {
                        if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                        else coveredPropositionEdge  
                        }}          
                    }else {
                    if(idx + 1 < deductionQueries.size) analyzeEdge(idx+1, deductionQueries, edge, aso, neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap, transversalState:TransversalState)
                    else coveredPropositionEdge        
                    }
                }
            }
        }
    }
  
    private def analyze(idx:Int, deductionQueries:List[DeductionQuery],edge:KnowledgeBaseEdge, nodeMap: Map[String, KnowledgeBaseNode], neo4JUtils:Neo4JUtilsImpl, isNegativeMeaningMap:Map[String,Boolean], transversalState:TransversalState):Option[CoveredPropositionEdge] = {
        val deductionUnitName = conf.getString("TOPOSOID_DEDUCTION_UNIT_NAME")
        val jsonStr: String = neo4JUtils.getCypherQueryResult(deductionQueries(idx).query, "", transversalState)
        //If there is even one that does not match, it is useless to search further
        if (!jsonStr.equals("""{"records":[]}""")) {
            //ヒットするものがある場合
            val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]    
            Option(DeductionUtils.getCoveredPropositionEdge(edge, deductionQueries(idx).sourceAlias, deductionQueries(idx).destinationAlias, nodeMap,  neo4jRecords, deductionQueries(idx).relationMatchState, deductionUnitName, isNegativeMeaningMap, transversalState:TransversalState, deductionQueries(idx).featureSimilarityMap))        
        }else{
            None
        }
    }

    private def getCoveredPropositionEdge(edge: KnowledgeBaseEdge, sourceAlias:String, destinationAlias:String, nodeMap:Map[String, KnowledgeBaseNode], neo4jRecords: Neo4jRecords, relationMatchState:RelationMatchState, deductionUnitName:String, isNegativeMeaningMap:Map[String,Boolean], transversalState:TransversalState, featureSimilarityMap:Map[String, Float] = Map.empty[String, Float]):CoveredPropositionEdge = {
        //一旦どちらかのノードが埋まっていれば推論を進めるものとする。        
        val (isConfirmedSource, isConfirmedDestination)= relationMatchState match {
            case RelationMatchState.MATCHED_BOTH => (true, true)
            case RelationMatchState.MATCHED_SOURCE_NODE_ONLY => (true, false)
            case RelationMatchState.MATCHED_TARGET_NODE_ONLY => (false, true)
            case RelationMatchState.NOT_MATCHED_BOTH => (false, false)
        } 

        val existDenialWord:Boolean = nodeMap.filter(x => x._2.predicateArgumentStructure.isDenialWord && (x._2.predicateArgumentStructure.caseType.equals("文末") || x._2.predicateArgumentStructure.caseType.equals("ROOT"))).size > 0

        val sourceNodeSurface = nodeMap.get(edge.sourceId).get.asInstanceOf[KnowledgeBaseNode].predicateArgumentStructure.surface
        val destinationNodeSurface = nodeMap.get(edge.destinationId).get.asInstanceOf[KnowledgeBaseNode].predicateArgumentStructure.surface

        val sourceKnowledgeNodes = neo4jRecords.records.map(x => x.filter(y => y.key == sourceAlias).map(
        z => List(z.value.localNode, z.value.synonymNode, z.value.featureNode).flatten.head)).flatten.distinct

        val destinationKnowledgeNodes = neo4jRecords.records.map(x => x.filter(y => y.key == destinationAlias).map(
        z => List(z.value.localNode, z.value.synonymNode, z.value.featureNode).flatten.head)).flatten.distinct

        val sourceMatchedKnowledgeNodes:List[MatchedKnowledgeNode] = sourceAlias match {
            case "" => List.empty[MatchedKnowledgeNode]
            case _ => getMatchedKnowledgeNodes(edge, sourceKnowledgeNodes, nodeMap.get(edge.sourceId).get, featureSimilarityMap, deductionUnitName, isNegativeMeaningMap, existDenialWord, transversalState)
        }

        val destinationMatchedKnowledgeNodes:List[MatchedKnowledgeNode] = destinationAlias match {
            case "" =>  List.empty[MatchedKnowledgeNode]
            case _ => getMatchedKnowledgeNodes(edge, destinationKnowledgeNodes, nodeMap.get(edge.destinationId).get, featureSimilarityMap, deductionUnitName, isNegativeMeaningMap, existDenialWord, transversalState)
        }

        val sourceNode = CoveredPropositionNode(terminalId = edge.sourceId, terminalSurface = sourceNodeSurface, terminalUrl = "", matchedKnowledgeNodes=sourceMatchedKnowledgeNodes, isConfirmedSource)
        val destinationNode = CoveredPropositionNode(terminalId = edge.destinationId, terminalSurface = destinationNodeSurface, terminalUrl = "", matchedKnowledgeNodes=destinationMatchedKnowledgeNodes, isConfirmedDestination)        
        CoveredPropositionEdge(sourceNode = sourceNode, destinationNode = destinationNode)
    }

    private def getMatchedKnowledgeNodes(
            edge: KnowledgeBaseEdge, 
            serchedKnowledgeNodes:List[KnowledgeBaseNode | KnowledgeBaseSynonymNode | KnowledgeFeatureReference], 
            proopsitionNode: KnowledgeBaseNode,
            featureSimilarityMap:Map[String, Float],
            deductionUnitName:String,
            isNegativeMeaningMap:Map[String,Boolean], 
            existDenialWord:Boolean,
            transversalState:TransversalState
        ):List[MatchedKnowledgeNode]= {
        
        val emptyMatchedFeatureInfo = MatchedFeatureInfo("", FeatureType.UNSPECIFIED.index,  -1.0)

        serchedKnowledgeNodes.map(x => {
            x match {
                case a:KnowledgeBaseNode => {
                    MatchedKnowledgeNode(
                        propositionId = a.propositionId,
                        sentenceId = a.sentenceId,
                        nodeId = a.nodeId,
                        caseNameOnEdge = edge.caseStr,
                        isDenialWord = a.predicateArgumentStructure.isDenialWord,
                        nodeType = a.predicateArgumentStructure.nodeType,
                        featureInfo = emptyMatchedFeatureInfo,
                        deductionUnit = deductionUnitName,
                        isNegativeMeaning = checkNegativeMatch(Neo4JUtilsImpl(), a.sentenceId, existDenialWord, isNegativeMeaningMap, transversalState)
                    )
                }
                case b:KnowledgeBaseSynonymNode => {
                    MatchedKnowledgeNode(
                        propositionId = b.propositionId,
                        sentenceId = b.sentenceId,
                        nodeId = b.nodeId,
                        caseNameOnEdge = edge.caseStr,
                        isDenialWord = proopsitionNode.predicateArgumentStructure.isDenialWord,
                        nodeType = proopsitionNode.predicateArgumentStructure.nodeType, 
                        featureInfo = emptyMatchedFeatureInfo,
                        deductionUnit = deductionUnitName,
                        isNegativeMeaning = checkNegativeMatch(Neo4JUtilsImpl(), b.sentenceId, existDenialWord, isNegativeMeaningMap, transversalState)
                    )
                }
                case c:KnowledgeFeatureReference => {

                    val matchedFeatureInfo = c.featureType match {
                        case FeatureType.IMAGE.index => {
                            MatchedFeatureInfo(c.featureId, FeatureType.IMAGE.index, featureSimilarityMap.getOrElse(c.featureId, -1.0F))
                        }
                        case FeatureType.TABLE.index => {
                            MatchedFeatureInfo(c.featureId, FeatureType.TABLE.index, featureSimilarityMap.getOrElse(c.featureId, -1.0F))
                        }
                        case _ => {
                            emptyMatchedFeatureInfo    
                        }
                    }
                    
                    MatchedKnowledgeNode(
                        propositionId = c.propositionId,
                        sentenceId = c.sentenceId,
                        nodeId = c.featureId,                        
                        caseNameOnEdge = edge.caseStr,
                        isDenialWord = proopsitionNode.predicateArgumentStructure.isDenialWord,
                        nodeType = proopsitionNode.predicateArgumentStructure.nodeType, 
                        featureInfo = matchedFeatureInfo,
                        deductionUnit = deductionUnitName,
                        isNegativeMeaning = checkNegativeMatch(Neo4JUtilsImpl(), c.sentenceId, existDenialWord, isNegativeMeaningMap, transversalState)
                    )
                }
            }}
        )    
    }

    //SentenceIdごとに文末の否定、肯定の一致があるかチェックする。
    private def checkNegativeMatch(neo4jUtils:Neo4JUtilsImpl, sentenceId:String, existDenialWord:Boolean, isNegativeMeaningMap:Map[String,Boolean], transversalState:TransversalState): Boolean = {        
        isNegativeMeaningMap.get(sentenceId) match {
            case Some(v) => v
            case _  => {
                val query = "MATCH (n) WHERE n.sentenceId='%s' AND n.isDenialWord='%s' AND (n.caseType='ROOT' OR n.caseType='文末') RETURN n".format(sentenceId, existDenialWord)
                val jsonStr: String = neo4jUtils.getCypherQueryResult(query, "", transversalState)
                val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
                neo4jRecords.records.size == 0
            }
        }
    }
    
}
