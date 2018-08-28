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
package edu.emory.cci.aiw.cvrg.eureka.etl.job;

import com.google.inject.persist.Transactional;
import org.protempa.DataSourceFailedDataValidationException;
import org.protempa.PropositionDefinition;
import org.protempa.Protempa;
import org.protempa.ProtempaStartupException;
import org.protempa.SourceFactory;
import org.protempa.backend.Configurations;
import org.protempa.backend.ConfigurationsLoadException;
import org.protempa.backend.ConfigurationsNotFoundException;
import org.protempa.backend.dsb.DataValidationEvent;
import org.protempa.backend.dsb.filter.Filter;
import org.protempa.query.DefaultQueryBuilder;
import org.protempa.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.cci.aiw.cvrg.eureka.etl.entity.JobEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.JobEventEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.config.EtlProperties;
import edu.emory.cci.aiw.cvrg.eureka.etl.config.EurekaProtempaConfigurations;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.DestinationDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.EtlGroupDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.JobDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dest.ProtempaDestinationFactory;
import edu.emory.cci.aiw.cvrg.eureka.etl.resource.Destinations;
import edu.emory.cci.aiw.cvrg.eureka.etl.resource.EtlDestinationToDestinationEntityVisitor;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import javax.inject.Inject;
import org.eurekaclinical.eureka.client.comm.JobStatus;
import org.eurekaclinical.protempa.client.comm.EtlDestination;
import org.protempa.ProtempaEvent;
import org.protempa.backend.Configuration;
import org.protempa.backend.InvalidPropertyNameException;
import org.protempa.backend.InvalidPropertyValueException;
import org.protempa.query.QueryMode;

/**
 * This class actually runs Protempa.
 * <p/>
 * There are two configuration files for each Protempa job. One, an INI file,
 * configures Protempa. Another, an XML file, configures the i2b2 query results
 * handler. Each Protempa configuration (an INI file) has associated with it one
 * i2b2 query results handler configuration file (an XML file). They are
 * associated by name. An INI file named my_config.ini has an associated XML
 * file my_config.xml. The INI and XML files go into a directory specified in
 * this class' constructor. The default is <code>/etc/eureka/etlconfig</code>.
 *
 * @author Andrew Post
 */
public class ETL {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETL.class);
    private static final String DATABASE_DIR = System.getProperty("eurekaclinical.protempaservice.database.directory");
    private static final int PROTEMPA_EVENT_QUEUE_MAX = 1000;
    private final EtlProperties etlProperties;
    private final DestinationDao destinationDao;
    private final ProtempaDestinationFactory protempaDestFactory;
    private final EtlGroupDao groupDao;
    private final EtlDestinationToDestinationEntityVisitor destToDestEntityVisitor;

    @Inject
    public ETL(EtlProperties inEtlProperties, DestinationDao inDestinationDao,
            EtlGroupDao inGroupDao, EtlDestinationToDestinationEntityVisitor inDestToDestEntityVisitor,
            ProtempaDestinationFactory inProtempaDestFactory) {
        this.etlProperties = inEtlProperties;
        this.destinationDao = inDestinationDao;
        this.protempaDestFactory = inProtempaDestFactory;
        this.groupDao = inGroupDao;
        this.destToDestEntityVisitor = inDestToDestEntityVisitor;
    }

    void run(JobDao inJobDao, Long inJobId, PropositionDefinition[] inPropositionDefinitions,
            String[] inPropIdsToShow, Filter filter,
            Configuration prompts) throws EtlException {
        assert inPropositionDefinitions != null :
                "inPropositionDefinitions cannot be null";
        assert inJobDao != null : "inJobDao cannot be null";
        JobEntity job = retrieveJob(inJobDao, inJobId);
        String databaseDirectory = DATABASE_DIR;
        if (databaseDirectory == null) {
            databaseDirectory = this.etlProperties.getDatabaseDirectory();
        }
        Queue<JobEventEntity> jobEventQueue = new LinkedList<>();
        try (Protempa protempa = getNewProtempa(job, prompts)) {
            LOGGER.debug("Validating the data source backend data for job {}", job.getId());
            logValidationEvents(inJobDao, job, protempa.validateDataSourceBackendData(), jobEventQueue, null);

            EtlDestination eurekaDestination;
            org.protempa.dest.Destination protempaDestination;
            eurekaDestination
                    = new Destinations(this.etlProperties, job.getUser(),
                            this.destinationDao, this.groupDao, this.destToDestEntityVisitor)
                            .getOne(job.getDestination().getName());

            File databaseFile;
            if (databaseDirectory != null) {
                databaseFile = new File(databaseDirectory, eurekaDestination.getName());
                createDatabaseDirectory(databaseFile.getParent());
            } else {
                databaseFile = null;
            }

            QueryMode queryMode = QueryMode.valueOf(job.getJobMode().getName());

            protempaDestination
                    = this.protempaDestFactory.getInstance(eurekaDestination.getId(), queryMode);

            LOGGER.debug("Constructing Protempa query for job {}", job.getId());
            DefaultQueryBuilder q = new DefaultQueryBuilder();
            q.setPropositionDefinitions(inPropositionDefinitions);
            if (!eurekaDestination.isAllowingQueryPropositionIds()) {
                LOGGER.debug("Querying the concepts specified by the destination for job {}", job.getId());
                q.setPropositionIds(protempa.getSupportedPropositionIds(protempaDestination));
            } else {
                q.setPropositionIds(inPropIdsToShow);
            }
            q.setName(job.getName());
            q.setUsername(job.getUser().getUsername());
            q.setFilters(filter);
            q.setQueryMode(queryMode);
            if (databaseFile != null) {
                q.setDatabasePath(databaseFile.getPath());
            }

            Query query = protempa.buildQuery(q);

            protempa.addEventListener((ProtempaEvent protempaEvent) -> {
                synchronized (job) {
                    JobEventEntity protempaEvt = new JobEventEntity();
                    protempaEvt.setTimeStamp(protempaEvent.getTimestamp());
                    protempaEvt.setStatus(JobStatus.STARTED);
                    protempaEvt.setMessage(protempaEvent.getType() + " " + protempaEvent.getDescription());
                    jobEventQueue.add(protempaEvt);
                    updateJob(inJobDao, job, jobEventQueue);
                }
            });
            LOGGER.debug("Executing Protempa query {}", q);
            protempa.execute(query, protempaDestination);
        } catch (DataSourceFailedDataValidationException ex) {
            logValidationEvents(inJobDao, job, ex.getValidationEvents(), jobEventQueue, ex);
            throw new EtlException("ETL failed for job " + job.getId(), ex);
        } catch (Exception ex) {
            throw new EtlException("ETL failed for job " + job.getId(), ex);
        } finally {
            updateJob(inJobDao, job, jobEventQueue);
        }
    }

    @Transactional
    private JobEntity retrieveJob(JobDao jobDao, Long jobId) {
        return jobDao.retrieve(jobId);
    }

    @Transactional
    private void updateJob(JobDao jobDao, JobEntity job, Queue<JobEventEntity> jobEvents) {
        if (!jobEvents.isEmpty() && jobEvents.size() % PROTEMPA_EVENT_QUEUE_MAX == 0) {
            JobEntity refresh = jobDao.refresh(job);
            JobEventEntity jobEvent;
            while ((jobEvent = jobEvents.poll()) != null) {
                jobEvent.setJob(refresh);
            }
            jobDao.update(refresh);
        }
    }

    private void createDatabaseDirectory(String databaseDirectory) throws EtlException {
        File databaseDirectoryFile = new File(databaseDirectory);
        if (!databaseDirectoryFile.exists()) {
            try {
                if (!databaseDirectoryFile.mkdirs()) {
                    throw new EtlException("Database directory " + databaseDirectory + " was not created");
                }
                LOGGER.info("Created Protempa database directory {}", databaseDirectory);
            } catch (SecurityException ex) {
                throw new EtlException("Database directory " + databaseDirectory + " was not created", ex);
            }
        }
    }

    private void logValidationEvents(JobDao jobDao, JobEntity job, DataValidationEvent[] events, Queue<JobEventEntity> jobEvents, DataSourceFailedDataValidationException ex) {
        for (DataValidationEvent event : events) {
            AbstractFileInfo fileInfo;
            JobStatus jobEventType;
            if (event.isFatal()) {
                fileInfo = new FileError();
                jobEventType = JobStatus.ERROR;
            } else {
                fileInfo = new FileWarning();
                jobEventType = JobStatus.WARNING;
            }
            fileInfo.setLineNumber(event.getLine());
            fileInfo.setText(event.getMessage());
            fileInfo.setType(event.getType());
            fileInfo.setURI(event.getURI());
            JobEventEntity validationJobEvent = new JobEventEntity();
            validationJobEvent.setTimeStamp(event.getTimestamp());
            validationJobEvent.setStatus(jobEventType);
            validationJobEvent.setMessage(fileInfo.toUserMessage());
            validationJobEvent.setExceptionStackTrace(collectThrowableMessages(ex));
            jobEvents.add(validationJobEvent);
        }
        updateJob(jobDao, job, jobEvents);
    }

    private Protempa getNewProtempa(JobEntity job, Configuration prompts) throws
            NewProtempaException {
        try {
            Configurations configurations = new EurekaProtempaConfigurations(this.etlProperties);
            Configuration configuration = configurations.load(job.getSourceConfigId());
            configuration.merge(prompts);
            SourceFactory sf = new SourceFactory(configuration);
            return Protempa.newInstance(sf);
        } catch (IOException | ConfigurationsLoadException | ProtempaStartupException | ConfigurationsNotFoundException | InvalidPropertyNameException | InvalidPropertyValueException ex) {
            throw new NewProtempaException("Error creating Protempa for sourceconfig " + job.getSourceConfigId() + " for job " + job.getId(), ex);
        }
    }

    private static String collectThrowableMessages(Throwable throwable) {
        String msg = throwable.getMessage();
        Throwable cause = throwable.getCause();
        if (cause != null) {
            msg += ": " + cause.getMessage();
        }
        return msg;
    }
}
