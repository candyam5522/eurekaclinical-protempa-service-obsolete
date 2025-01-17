/*
 * #%L
 * Eureka Protempa ETL
 * %%
 * Copyright (C) 2012 - 2013 Emory University
 * %%
 * This program is dual licensed under the Apache 2 and GPLv3 licenses.
 * 
 * Apache License, Version 2.0:
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * GNU General Public License version 3:
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package edu.emory.cci.aiw.cvrg.eureka.etl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.protempa.backend.dsb.filter.DateTimeFilter;
import org.protempa.proposition.value.AbsoluteTimeGranularity;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.eurekaclinical.eureka.client.comm.Job;
import org.eurekaclinical.eureka.client.comm.JobFilter;
import org.eurekaclinical.protempa.client.comm.JobRequest;
import org.eurekaclinical.eureka.client.comm.JobSpec;
import org.eurekaclinical.eureka.client.comm.SourceConfig;
import org.eurekaclinical.eureka.client.comm.SourceConfigOption;
import org.eurekaclinical.eureka.client.comm.Statistics;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.DestinationEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.AuthorizedUserEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.JobEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.config.EurekaProtempaConfigurations;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.DestinationDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.AuthorizedUserDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.config.EtlProperties;
import edu.emory.cci.aiw.cvrg.eureka.etl.conversion.ConversionSupport;
import edu.emory.cci.aiw.cvrg.eureka.etl.conversion.ConversionUtil;
import edu.emory.cci.aiw.cvrg.eureka.etl.conversion.PropositionDefinitionCollector;
import edu.emory.cci.aiw.cvrg.eureka.etl.conversion.PropositionDefinitionConverterVisitor;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.JobDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.JobModeDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.job.TaskManager;
import edu.emory.cci.aiw.cvrg.eureka.etl.dest.ProtempaDestinationFactory;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.JobModeEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.job.Task;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.eurekaclinical.common.comm.clients.ClientException;
import org.eurekaclinical.common.auth.AuthorizedUserSupport;
import org.eurekaclinical.common.comm.clients.ClientException;
import org.eurekaclinical.eureka.client.comm.JobSpec.Side;
import org.eurekaclinical.eureka.client.comm.Phenotype;
import org.eurekaclinical.eureka.client.comm.PhenotypeVisitor;
import org.eurekaclinical.eureka.client.comm.exception.PhenotypeHandlingException;
import org.eurekaclinical.phenotype.client.EurekaClinicalPhenotypeClient;
import org.eurekaclinical.standardapis.exception.HttpStatusException;
import org.protempa.PropositionDefinition;
import org.protempa.backend.BackendInstanceSpec;
import org.protempa.backend.BackendProviderSpecLoaderException;
import org.protempa.backend.BackendSpecNotFoundException;
import org.protempa.backend.Configuration;
import org.protempa.backend.InvalidPropertyNameException;
import org.protempa.backend.InvalidPropertyValueException;
import org.protempa.backend.dsb.DataSourceBackend;
import org.protempa.dest.Destination;
import org.protempa.dest.DestinationInitException;
import org.protempa.dest.StatisticsException;
import org.protempa.proposition.interval.Interval;
import org.protempa.query.QueryMode;

@Path("/protected/jobs")
//@RolesAllowed({"researcher"})
@Consumes(MediaType.APPLICATION_JSON)
public class JobResource {

    private final JobDao jobDao;
    private final AuthorizedUserDao etlUserDao;
    private final TaskManager taskManager;
    private final AuthorizedUserSupport<AuthorizedUserEntity, AuthorizedUserDao, ?> authenticationSupport;
    private final DestinationDao destinationDao;
    private final ProtempaDestinationFactory protempaDestinationFactory;
    private final EtlProperties etlProperties;
    private final Provider<Task> taskProvider;
    private final JobModeDao jobModeDao;
    private final EurekaClinicalPhenotypeClient phenotypeClient;
    private final PropositionDefinitionConverterVisitor converterVisitor;
    private final ConversionSupport conversionSupport;



    @Inject
    public JobResource(JobDao inJobDao, TaskManager inTaskManager, AuthorizedUserDao inEtlUserDao,
            DestinationDao inDestinationDao, EtlProperties inEtlProperties,
            ProtempaDestinationFactory inProtempaDestinationFactory,
            Provider<Task> inTaskProvider, JobModeDao inJobModeDao,
            EurekaClinicalPhenotypeClient inPhenotypeClient,
            PropositionDefinitionConverterVisitor  inConverterVisitor,
            ConversionSupport inConversionSupport
            ) {
        this.jobDao = inJobDao;
        this.taskManager = inTaskManager;
        this.etlUserDao = inEtlUserDao;
        this.authenticationSupport = new AuthorizedUserSupport<>(this.etlUserDao);
        this.destinationDao = inDestinationDao;
        this.etlProperties = inEtlProperties;
        this.protempaDestinationFactory = inProtempaDestinationFactory;
        this.taskProvider = inTaskProvider;
        this.jobModeDao = inJobModeDao;
        this.phenotypeClient = inPhenotypeClient;
        this.converterVisitor = inConverterVisitor;
        this.conversionSupport = inConversionSupport;
    }

    @Transactional
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Job> getAll(@Context HttpServletRequest request, @QueryParam("order") String order) {
        JobFilter jobFilter = new JobFilter(null, this.authenticationSupport.getUser(request).getId(), null, null, null,
                null);
        List<Job> jobs = new ArrayList<>();
        List<JobEntity> jobEntities;
        if (order == null || order.equals("asc")) {
            jobEntities = this.jobDao.getWithFilter(jobFilter);
        } else if (order.equals("desc")) {
            jobEntities = this.jobDao.getWithFilterDesc(jobFilter);
        } else {
            throw new HttpStatusException(Response.Status.PRECONDITION_FAILED,
                    "Invalid value for the order parameter: " + order);
        }
        for (JobEntity jobEntity : jobEntities) {
            jobs.add(jobEntity.toJob());
        }
        return jobs;
    }

    @Transactional
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/all")
    @RolesAllowed({"admin"})
    public List<Job> getAllJobs(@Context HttpServletRequest request, @QueryParam("order") String order) {
        // JobFilter jobFilter = new JobFilter(null,
        // this.authenticationSupport.getUser(request).getId(), null, null, null, null);
        List<Job> jobs = new ArrayList<>();
        List<JobEntity> jobEntities;
        if (order == null) {
            jobEntities = this.jobDao.getAll();
        } // else if (order.equals("desc")) {
        // jobEntities = this.jobDao.getWithFilterDesc(jobFilter);
        // }
        else {
            throw new HttpStatusException(Response.Status.PRECONDITION_FAILED,
                    "Invalid value for the order parameter: " + order);
        }
        for (JobEntity jobEntity : jobEntities) {
            jobs.add(jobEntity.toJob());
        }
        return jobs;
    }

    @Transactional
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}")
    public Job getJob(@Context HttpServletRequest request, @PathParam("jobId") Long inJobId) {
        return getJobEntity(request, inJobId).toJob();
    }

    @Transactional
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}/stats/{propId}")
    public org.eurekaclinical.eureka.client.comm.Statistics getJobStats(@Context HttpServletRequest request,
            @PathParam("jobId") Long inJobId, @PathParam("propId") String inPropId) {
        Job job = getJob(request, inJobId);
        String destinationId = job.getDestinationId();
        DestinationEntity destEntity = this.destinationDao.getCurrentByName(destinationId);
        if (destEntity != null) {
            try {
                Destination dest = this.protempaDestinationFactory.getInstance(destEntity, QueryMode.REPLACE);
                Statistics result = new Statistics();
                org.protempa.dest.Statistics statistics = dest.getStatistics();
                if (statistics != null) {
                    result.setNumberOfKeys(statistics.getNumberOfKeys());
                    result.setCounts(statistics.getCounts(inPropId != null ? new String[]{inPropId} : null));
                    result.setChildrenToParents(
                            statistics.getChildrenToParents(inPropId != null ? new String[]{inPropId} : null));
                }
                return result;
            } catch (DestinationInitException | StatisticsException ex) {
                throw new HttpStatusException(Status.INTERNAL_SERVER_ERROR, "Error getting stats", ex);
            }
        } else {
            throw new HttpStatusException(Status.INTERNAL_SERVER_ERROR, "Invalid destination id " + destinationId);
        }
    }

    @Transactional
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}/stats")
    public org.eurekaclinical.eureka.client.comm.Statistics getJobStatsRoot(@Context HttpServletRequest request,
            @PathParam("jobId") Long inJobId) {
        return getJobStats(request, inJobId, null);
    }


    // Finer grained transactions in the implementation
    @POST
    public Response submit(@Context HttpServletRequest request, JobSpec jobSpec) {
  
        JobRequest jobRequest = new JobRequest();
        jobRequest.setJobSpec(jobSpec);
        
        try{
            ConversionUtil.setupTimeUnitAndOperators(this.phenotypeClient);
            List<PropositionDefinition> lisUserPropDefs = this.getUserPropositionDefinitions();
            jobRequest.setUserPropositions(lisUserPropDefs);
        } catch (ClientException ex) {
            throw new HttpStatusException(Status.INTERNAL_SERVER_ERROR, ex);
        } catch (PhenotypeHandlingException ex) {
            Logger.getLogger(JobResource.class.getName()).log(Level.SEVERE, null, ex);
            throw new HttpStatusException(Status.INTERNAL_SERVER_ERROR, ex);
        }
        
        List<String> conceptIds = jobSpec.getPropositionIds();
        List<String> propIds = new ArrayList<>(conceptIds != null ? conceptIds.size() : 0);
        if (conceptIds != null) {
            for (String conceptId : conceptIds) {
                propIds.add(this.conversionSupport.toPropositionId(conceptId));
            }
        }
        jobRequest.setPropositionIdsToShow(propIds);

        validate(jobRequest);

        Long jobId;

        jobId = doCreateJob(jobRequest, request);
     

        return Response.created(URI.create("/" + jobId)).build();
    }

    @Transactional
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/latest")
    public List<Job> getLatestJob(@Context HttpServletRequest request) {
        List<Job> jobs = new ArrayList<>();
        List<JobEntity> jobEntities;
        JobFilter jobFilter = new JobFilter(null, this.authenticationSupport.getUser(request).getId(), null, null, null,
                true);
        jobEntities = this.jobDao.getLatestWithFilter(jobFilter);
        for (JobEntity jobEntity : jobEntities) {
            jobs.add(jobEntity.toJob());
        }
        return jobs;
    }

    private JobEntity getJobEntity(HttpServletRequest request, Long inJobId) {
        JobFilter jobFilter = new JobFilter(inJobId, this.authenticationSupport.getUser(request).getId(), null, null,
                null, null);
        List<JobEntity> jobEntities = this.jobDao.getWithFilter(jobFilter);
        if (jobEntities.isEmpty()) {
            throw new HttpStatusException(Status.NOT_FOUND);
        } else if (jobEntities.size() > 1) {
            throw new HttpStatusException(Status.INTERNAL_SERVER_ERROR,
                    jobEntities.size() + " jobs returned for job id " + inJobId);
        } else {
            return jobEntities.get(0);
        }
    }

    private Long doCreateJob(JobRequest inJobRequest, HttpServletRequest request) {
        JobSpec jobSpec = inJobRequest.getJobSpec();
        Configuration prompts = toConfiguration(jobSpec.getPrompts());
        JobEntity jobEntity = newJobEntity(jobSpec, this.authenticationSupport.getUser(request));
        DateTimeFilter dateTimeFilter;
        String dateRangePhenotypeKey = jobSpec.getDateRangePhenotypeKey();
        if (dateRangePhenotypeKey != null) {
            dateTimeFilter = new DateTimeFilter(new String[]{dateRangePhenotypeKey}, jobSpec.getEarliestDate(),
                    AbsoluteTimeGranularity.DAY, jobSpec.getLatestDate(), AbsoluteTimeGranularity.DAY,
                    toProtempaSide(jobSpec.getEarliestDateSide()), toProtempaSide(jobSpec.getLatestDateSide()));
        } else {
            dateTimeFilter = null;
        }
        Task task = this.taskProvider.get();
        task.setJobId(jobEntity.getId());
        task.setPropositionDefinitions(inJobRequest.getUserPropositions());
        task.setPropositionIdsToShow(inJobRequest.getPropositionIdsToShow());
        task.setFilter(dateTimeFilter);
        task.setPrompts(prompts);
        this.taskManager.queueTask(task);
        return jobEntity.getId();
    }
  
    public String toPropositionId(String phenotypeKey) {
        final  String PRIMARY_PROP_ID_SUFFIX = "_PRIMARY";
        final  String VALUE = "YES";
        final  String VALUE_COMP = "NO";
        final String PROP_ID_WRAPPED_SUFFIX = "_WRAPPED";
        final String USER_KEY_PREFIX = "USER:";

            if (phenotypeKey == null || !phenotypeKey.startsWith(USER_KEY_PREFIX)) {
                    return phenotypeKey;
            } else {
                    return phenotypeKey + PRIMARY_PROP_ID_SUFFIX;
            }
    }
    
    private static Interval.Side toProtempaSide(Side side) {
        switch (side) {
            case START:
                return Interval.Side.START;
            case FINISH:
                return Interval.Side.FINISH;
            default:
                throw new AssertionError("Unexpected side " + side);
        }
    }

    private JobEntity newJobEntity(JobSpec jobSpec, AuthorizedUserEntity etlUser) {
        JobEntity jobEntity = new JobEntity();
        String sourceConfigId = jobSpec.getSourceConfigId();
        if (sourceConfigId == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "sourceconfig must be specified");
        }
        jobEntity.setSourceConfigId(sourceConfigId);
        String destinationId = jobSpec.getDestinationId();
        if (destinationId == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "destination is required");
        }
        Long jobMode = jobSpec.getJobMode();
        if (jobMode == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "jobMode is required");
        }
        
        JobModeEntity jobModeEntity = this.jobModeDao.retrieve(jobSpec.getJobMode());
        if (jobModeEntity == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "unknown jobMode " + jobMode);
        } else if (jobModeEntity.getName().equals("UNKNOWN")) {
            throw new HttpStatusException(Status.BAD_REQUEST, "jobMode UNKNOWN is only for jobs that predate job modes and cannot be set on new jobs");
        }
        
        createJob(destinationId, jobSpec, jobEntity, etlUser, jobModeEntity);
        return jobEntity;
    }

    @Transactional
    public void createJob(String destinationId, JobSpec jobSpec, JobEntity jobEntity, AuthorizedUserEntity etlUser, JobModeEntity jobModeEntity) throws HttpStatusException {
        DestinationEntity destination = this.destinationDao.getCurrentByName(destinationId);
        if (destination == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "Invalid destination " + jobSpec.getDestinationId());
        }
        jobEntity.setDestination(destination);
        jobEntity.setCreated(new Date());
        jobEntity.setUser(etlUser);
        jobEntity.setName(jobSpec.getName());
        jobEntity.setJobMode(jobModeEntity);
        
        this.jobDao.create(jobEntity);
    }

    private Configuration toConfiguration(SourceConfig prompts) {
        if (prompts != null) {
            Configuration result = new Configuration();
            SourceConfig.Section[] dsbSections = prompts.getDataSourceBackends();
            List<BackendInstanceSpec<DataSourceBackend>> sections = new ArrayList<>();
            EurekaProtempaConfigurations configurations;
            try {
                configurations = new EurekaProtempaConfigurations(this.etlProperties);
            } catch (IOException ex) {
                throw new HttpStatusException(Status.INTERNAL_SERVER_ERROR, ex);
            }
            for (int i = 0; i < dsbSections.length; i++) {
                SourceConfig.Section section = dsbSections[i];
                try {
                    BackendInstanceSpec<DataSourceBackend> bis = configurations
                            .newDataSourceBackendSection(section.getId());
                    SourceConfigOption[] options = section.getOptions();
                    for (SourceConfigOption option : options) {
                        bis.setProperty(option.getName(), option.getValue());
                    }
                    sections.add(bis);
                } catch (BackendSpecNotFoundException | BackendProviderSpecLoaderException
                        | InvalidPropertyNameException | InvalidPropertyValueException ex) {
                    throw new HttpStatusException(Status.BAD_REQUEST, ex);
                }
            }
            result.setDataSourceBackendSections(sections);
            return result;
        } else {
            return null;
        }
    }

    private void validate(JobRequest inJobRequest) {
        JobSpec jobSpec = inJobRequest.getJobSpec();
        if (jobSpec == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "jobSpec is a required field");
        }
        if (jobSpec.getJobMode() == null) {
            throw new HttpStatusException(Status.BAD_REQUEST, "The jobSpec must have a jobMode");
        }
    }
    
    List<PropositionDefinition> getUserPropositionDefinitions() throws PhenotypeHandlingException, ClientException{
        List<PropositionDefinition> propositionList;
                List<Phenotype> phenotypeList;
                phenotypeList = this.phenotypeClient.getUserPhenotypes(false);
                this.converterVisitor.setAllCustomPhenotypes(phenotypeList);
                PropositionDefinitionCollector collector
                    = PropositionDefinitionCollector.getInstance(
						this.converterVisitor, phenotypeList);
                propositionList = collector.getUserPropDefs();
                  
		return propositionList;
    }

}
