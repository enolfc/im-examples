/*
 *  (c) Copyright 2018 EGI Foundation
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at

 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package eu.egi.fedcloud;

import java.util.concurrent.TimeUnit;

import java.net.URL;
import java.net.MalformedURLException;

import java.nio.file.Paths;

import es.upv.i3m.grycap.file.DoubleEscapeNewLinesFile;
import es.upv.i3m.grycap.file.NoNullOrEmptyFile;
import es.upv.i3m.grycap.file.Utf8File;

import es.upv.i3m.grycap.im.InfrastructureManager;
import es.upv.i3m.grycap.im.auth.credentials.AuthorizationHeader;
import es.upv.i3m.grycap.im.auth.credentials.providers.OcciCredentials;
import es.upv.i3m.grycap.im.auth.credentials.providers.ImCredentials;
import es.upv.i3m.grycap.im.exceptions.ImClientException;
import es.upv.i3m.grycap.im.pojo.InfrastructureUri;
import es.upv.i3m.grycap.im.pojo.Property;
import es.upv.i3m.grycap.im.rest.client.BodyContentType;
import es.upv.i3m.grycap.im.States;
import es.upv.i3m.grycap.im.VmProperties;


public class ImExample 
{
    public static void waitForInfrastructure(InfrastructureManager im, InfrastructureUri infraUri) throws ImClientException 
    {
        try {
            States st;
            do {
                TimeUnit.SECONDS.sleep(60);
                st = im.getInfrastructureState(infraUri.getInfrastructureId()).getEnumState();
                System.out.println("STATE: " + st);
            } while (st != States.CONFIGURED && st != States.FAILED);
            //
            // show some info about the VM (e.g. IP)
            for (InfrastructureUri vmUri: im.getInfrastructureInfo(infraUri.getInfrastructureId()).getUris()) {
                String vmId = Paths.get((new URL(vmUri.getUri())).getPath()).getFileName().toString();
                Property vmIP = im.getVmProperty(infraUri.getInfrastructureId(), vmId, VmProperties.NET_INTERFACE_0_IP);
	            System.out.println("IP of VM " + vmId + ": " + vmIP.getValue());
            }
       } catch (InterruptedException | MalformedURLException ex) {
           throw new RuntimeException(ex);
       }
    }


    public static void main (String[] args)
    {
        // IM Server endpoint
        String IM_ENDPOINT = "https://servproject.i3m.upv.es:8800";
        // This is a sample code, do not hardcode credentials in your code!
        String IM_USER = "<your user>"; // <= Change here!
        String IM_PASSWD = "<your password>"; // <= Change here!
        // This can be easily discovered using AppDB IS
        String X509_USER_PROXY = "/tmp/x509up_u501"; // <= Change to match your proxy location (voms-proxy-info -path)

        // Change as needed
        String OCCI_ENDPOINT = "https://sbgcloud.in2p3.fr:8787/occi1.2";

        try {
            // Prepare authentication header to IM
            String proxy = new DoubleEscapeNewLinesFile(
                    new NoNullOrEmptyFile(
                        new Utf8File(Paths.get(X509_USER_PROXY))
                    )).read();
            AuthorizationHeader ah = new AuthorizationHeader();
            ah.addCredential(ImCredentials.buildCredentials()
                                          .withUsername(IM_USER)
                                          .withPassword(IM_PASSWD));
            // This can be changed to other type of credentials as needed
            ah.addCredential(OcciCredentials.buildCredentials()
                                            .withHost(OCCI_ENDPOINT)
                                            .withProxy(proxy));

            InfrastructureManager im = new InfrastructureManager(IM_ENDPOINT, ah);

            // List existing infras, should be empty... 

            System.out.println("Existing infrastructures:");
            for (InfrastructureUri uri: im.getInfrastructureList().getUris()) {
                System.out.println(uri.getInfrastructureId());
            }


            // create a simple VM
            // Check RADL specification at http://imdocs.readthedocs.io/en/latest/radl.html
            String vmRadl = "network public (outbound = 'yes' )\n"
                          + "system vm (\n"
                          // this is taken from AppDB IS
                          + "instance_type = '2' and\n"
                          + "net_interface.0.connection = 'public' and\n"
                          + "net_interface.0.dns_name = 'master' and\n"
                          + "disk.0.os.name = 'linux' and\n"
                          // Again ids from AppDB IS
                          + "disk.0.image.url= ['https://sbgcloud.in2p3.fr:8787/occi1.2/9e7c0d7c-84c6-4b1f-95a1-c541b4a8310d'] and\n"
                          + "disk.0.os.credentials.username = 'cloudadm'\n"
                          + ")\n"
                          + "deploy vm 1\n";

            InfrastructureUri infraUri =  im.createInfrastructure(vmRadl, BodyContentType.RADL);

            waitForInfrastructure(im, infraUri);

            // modify the infrastructure, add one VM more
            String newVMRadl = "network public\n"
                             + "system vm\n"
                             + "deploy vm 1\n";
            InfrastructureUri vm2Uri = im.addResource(infraUri.getInfrastructureId(),
                                                      newVMRadl, BodyContentType.RADL).getUris().get(0); 

            waitForInfrastructure(im, infraUri);

            // remove the second VM
            im.removeResource(infraUri.getInfrastructureId(),
                              Paths.get((new URL(vm2Uri.getUri())).getPath()).getFileName().toString());

            waitForInfrastructure(im, infraUri);

            // delete the whole infrastructure
            im.destroyInfrastructure(infraUri.getInfrastructureId());
        } catch (ImClientException | MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
