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

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.sun.jersey.api.client.ClientResponse;

import org.eurekaclinical.eureka.client.comm.SourceConfig;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.AuthorizedUserEntity;
import edu.emory.cci.aiw.cvrg.eureka.etl.config.EtlProperties;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.EtlGroupDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.AuthorizedUserDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.dao.SourceConfigDao;
import edu.emory.cci.aiw.cvrg.eureka.etl.entity.SourceConfigEntity;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.arp.javautil.string.StringUtil;
import org.eurekaclinical.common.auth.AuthorizedUserSupport;
import org.eurekaclinical.eureka.client.comm.FileSourceConfigOption;
import org.eurekaclinical.eureka.client.comm.SourceConfigOption;
import org.eurekaclinical.eureka.client.comm.SourceConfigParams;
import org.eurekaclinical.standardapis.exception.HttpStatusException;
import org.protempa.backend.Configuration;
import org.protempa.backend.ConfigurationsSaveException;

@Transactional
@Path("/protected/sourceconfigs")
@RolesAllowed({"researcher"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SourceConfigsResource {

    private final EtlProperties etlProperties;
    private final AuthorizedUserDao userDao;
    private final SourceConfigDao sourceConfigDao;
    private final AuthorizedUserSupport<AuthorizedUserEntity, AuthorizedUserDao, ?> authenticationSupport;
    private final EtlGroupDao groupDao;

    @Inject
    public SourceConfigsResource(EtlProperties inEtlProperties, AuthorizedUserDao inUserDao, SourceConfigDao inSourceConfigDao, EtlGroupDao inGroupDao) {
        this.etlProperties = inEtlProperties;
        this.userDao = inUserDao;
        this.sourceConfigDao = inSourceConfigDao;
        this.authenticationSupport = new AuthorizedUserSupport<>(this.userDao);
        this.groupDao = inGroupDao;
    }

    @GET
    @Path("/{sourceConfigId}")
    public SourceConfig getSource(@Context HttpServletRequest req,
            @PathParam("sourceConfigId") String sourceConfigId) {
        AuthorizedUserEntity user = this.authenticationSupport.getUser(req);
        SourceConfigs sourceConfigs = new SourceConfigs(this.etlProperties, user, this.sourceConfigDao, this.groupDao);
        SourceConfig sourceConfig = sourceConfigs.getOne(sourceConfigId);
        if (sourceConfig != null) {
            return sourceConfig;
        } else {
            throw new HttpStatusException(Status.NOT_FOUND);
        }
    }
    
    @POST
    public Response createSourceConfig(@Context HttpServletRequest req,
             SourceConfig newSourceConfig) {
        AuthorizedUserEntity user = this.authenticationSupport.getUser(req);
        
        SourceConfigEntity scEntity = new SourceConfigEntity();
        scEntity.setName(newSourceConfig.getId());
        scEntity.setOwner(user);
                        
        SourceConfigs sourceConfigs = new SourceConfigs(this.etlProperties, user, this.sourceConfigDao, this.groupDao);
        try{
            if(sourceConfigs.SaveSourceConfig(newSourceConfig)==null){
                return Response.status(Status.NOT_ACCEPTABLE).build();
            }
        }catch (ConfigurationsSaveException ex){
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        
        this.sourceConfigDao.create(scEntity);
                
        return Response.created(URI.create("/"+newSourceConfig.getId())).build();
    }
    
    @PUT
    @Path("/{sourceConfigId}")
    public Response updateSourceConfig(@Context HttpServletRequest req,
             @PathParam("sourceConfigId") String sourceConfigId, SourceConfig newSourceConfig) {
        AuthorizedUserEntity user = this.authenticationSupport.getUser(req);
        
        SourceConfigEntity scEntity = new SourceConfigEntity();
        scEntity.setName(newSourceConfig.getId());
        scEntity.setOwner(user);
                        
    //    this.sourceConfigDao.create(scEntity);
        newSourceConfig.setId(sourceConfigId);
        SourceConfigs sourceConfigs = new SourceConfigs(this.etlProperties, user, this.sourceConfigDao, this.groupDao);
        try{
            sourceConfigs.SaveSourceConfig(newSourceConfig);
        }catch (ConfigurationsSaveException ex){
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        
        return Response.created(URI.create("/"+newSourceConfig.getId())).build();
    }


    @GET
    public List<SourceConfig> getAll(@Context HttpServletRequest req) {
        AuthorizedUserEntity user = this.authenticationSupport.getUser(req);
        SourceConfigs sourceConfigs = new SourceConfigs(this.etlProperties, user, this.sourceConfigDao, this.groupDao);
        return sourceConfigs.getAll();
    }
    
    @GET
    @Path("/parameters/list")
    public List<SourceConfigParams> getParamsList(@Context HttpServletRequest req) {
            List<SourceConfigParams> result = new ArrayList<>();

            for (SourceConfig config : this.getAll(req)) {
                    result.add(toParams(config));
            }

            return result;
    }

    @GET
    @Path("/parameters/{id}")
    public SourceConfigParams getParams(@Context HttpServletRequest req, @PathParam("id") String inId) {
            return toParams(this.getSource(req, inId));
    }

    private static SourceConfigParams toParams(SourceConfig config) {
            SourceConfigParams params = new SourceConfigParams();
            params.setId(config.getId());
            String displayName = config.getDisplayName();
            if (StringUtil.getEmptyOrNull(displayName)) {
                    displayName = config.getId();
            }
            params.setName(displayName);
            List<SourceConfigParams.Upload> uploads = new ArrayList<>();
            for (SourceConfig.Section section : config.getDataSourceBackends()) {
                    SourceConfigParams.Upload upload = null;
                    String sourceId = null;
                    String sampleUrl = null;
                    for (SourceConfigOption option : section.getOptions()) {
                            if (option instanceof FileSourceConfigOption) {
                                    upload = new SourceConfigParams.Upload();
                                    upload.setName(section.getDisplayName());
                                    upload.setAcceptedMimetypes(((FileSourceConfigOption) option).getAcceptedMimetypes());
                                    if (sourceId != null) {
                                            upload.setSourceId(sourceId);
                                    }
                                    if (sampleUrl != null) {
                                            upload.setSampleUrl(sampleUrl);
                                    }
                                    upload.setRequired(option.isRequired());
                            } else if (option.getName().equals("dataFileDirectoryName")) {
                                    Object val = option.getValue();
                                    if (val != null) {
                                            sourceId = val.toString();
                                            if (upload != null) {
                                                    upload.setSourceId(sourceId);
                                            }
                                    }
                            } else if (option.getName().equals("sampleUrl")) {
                                    Object val = option.getValue();
                                    if (val != null) {
                                            sampleUrl = val.toString();
                                            if (upload != null) {
                                                    upload.setSampleUrl(sampleUrl);
                                            }
                                    }
                            }
                    }
                    if (upload != null) {
                            uploads.add(upload);
                    }
            }
            params.setUploads(uploads.toArray(new SourceConfigParams.Upload[uploads.size()]));
            return params;
    }
    
    @GET
    @Path("/admin/list")
    public List<SourceConfigParams> getAdminList(@Context HttpServletRequest req) {
            List<SourceConfigParams> result = new ArrayList<>();

            for (SourceConfig config : this.getAll(req)) {
                    result.add(toParams(config));
            }

            return result;
    }

//    @GET
//    @Path("/admin/{id}")
//    public SourceConfigParams getAdmin(@Context HttpServletRequest req, @PathParam("id") String inId) {
//            return toParams(this.getSource(req, inId));
//    }
//    
}
