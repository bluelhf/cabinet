package blue.lhf.cabinette.plugins;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.bukkit.plugin.PluginDescriptionFile;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.*;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.*;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import static org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_FAIL;

public class EmbeddedLibraryLoader {
    private final Logger logger;
    private final RepositorySystem repository;
    private final DefaultRepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public EmbeddedLibraryLoader(final Logger logger) {
        this.logger = logger;

        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        this.repository = locator.getService(RepositorySystem.class);
        this.session = MavenRepositorySystemUtils.newSession();

        session.setChecksumPolicy(CHECKSUM_POLICY_FAIL);
        session.setLocalRepositoryManager(repository.newLocalRepositoryManager(session, new LocalRepository("libraries")));
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            public void transferStarted(final TransferEvent event) {
                logger.log(Level.INFO, "Downloading {0}", event.getResource().getRepositoryUrl() + event.getResource().getResourceName());
            }
        });
        session.setReadOnly();

        this.repositories = repository.newResolutionRepositories(session, Collections.singletonList(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build()));
    }

    public ClassLoader createLoader(final PluginDescriptionFile desc) throws DependencyResolutionException {
        if (desc.getLibraries().isEmpty()) {
            return null;
        }
        logger.log(Level.INFO, "[{0}] Loading {1} libraries... please wait", new Object[]{desc.getName(), desc.getLibraries().size()});

        final List<Dependency> dependencies = new ArrayList<>();
        for (final String library : desc.getLibraries()) {
            final Artifact artifact = new DefaultArtifact(library);
            final Dependency dependency = new Dependency(artifact, null);

            dependencies.add(dependency);
        }

        final DependencyResult result = repository.resolveDependencies(session, new DependencyRequest(new CollectRequest((Dependency) null, dependencies, repositories), null));
        final List<URL> jarFiles = new ArrayList<>();
        for (final ArtifactResult artifact : result.getArtifactResults()) {
            final File file = artifact.getArtifact().getFile();

            final URL url;
            try {
                url = file.toURI().toURL();
            } catch (final MalformedURLException ex) {
                throw new AssertionError(ex);
            }

            jarFiles.add(url);
            logger.log(Level.INFO, "[{0}] Loaded library {1}", new Object[]{desc.getName(), file});
        }

        return new URLClassLoader(jarFiles.toArray(new URL[0]), getClass().getClassLoader());
    }
}
