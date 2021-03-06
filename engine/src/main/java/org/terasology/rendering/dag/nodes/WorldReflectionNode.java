/*
 * Copyright 2016 MovingBlocks
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
 */
package org.terasology.rendering.dag.nodes;

import org.terasology.assets.ResourceUrn;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.registry.In;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.assets.shader.ShaderProgramFeature;
import org.terasology.rendering.backdrop.BackdropRenderer;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.dag.AbstractNode;
import org.terasology.rendering.dag.stateChanges.BindFBO;
import org.terasology.rendering.dag.stateChanges.EnableFaceCulling;
import org.terasology.rendering.dag.stateChanges.SetFacesToCull;
import org.terasology.rendering.dag.stateChanges.SetViewportToSizeOf;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;
import org.terasology.rendering.opengl.fbms.DisplayResolutionDependentFBOs;
import org.terasology.rendering.primitives.ChunkMesh;
import org.terasology.rendering.world.RenderQueuesHelper;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.rendering.world.WorldRendererImpl;
import static org.terasology.rendering.opengl.ScalingFactors.HALF_SCALE;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.GL_FRONT;

/**
 * Diagram of this node can be viewed from:
 * TODO: move diagram to the wiki when this part of the code is stable
 * - https://docs.google.com/drawings/d/1Iz7MA8Y5q7yjxxcgZW-0antv5kgx6NYkvoInielbwGU/edit?usp=sharing
 */
public class WorldReflectionNode extends AbstractNode {
    public static final ResourceUrn REFLECTED = new ResourceUrn("engine:sceneReflected");

    @In
    private RenderQueuesHelper renderQueues;

    @In
    private Config config;

    @In
    private WorldRenderer worldRenderer;

    @In
    private BackdropRenderer backdropRenderer;

    @In
    private DisplayResolutionDependentFBOs displayResolutionDependentFBOs;

    private Camera playerCamera;
    private Material chunkShader;
    private RenderingConfig renderingConfig;


    @Override
    public void initialise() {
        this.renderingConfig = config.getRendering();
        this.chunkShader = worldRenderer.getMaterial("engine:prog.chunk");
        this.playerCamera = worldRenderer.getActiveCamera();

        requiresFBO(new FBOConfig(REFLECTED, HALF_SCALE, FBO.Type.DEFAULT).useDepthBuffer(), displayResolutionDependentFBOs);
        addDesiredStateChange(new BindFBO(REFLECTED, displayResolutionDependentFBOs));
        addDesiredStateChange(new SetViewportToSizeOf(REFLECTED, displayResolutionDependentFBOs));
        addDesiredStateChange(new EnableFaceCulling());
        addDesiredStateChange(new SetFacesToCull(GL_FRONT));
    }

    @Override
    public void process() {
        PerformanceMonitor.startActivity("rendering/worldReflection");

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify if necessary
        playerCamera.setReflected(true);
        playerCamera.lookThroughNormalized(); // we don't want the reflected scene to be bobbing or moving with the player
        // TODO: convert backdropRenderer into a BackdropNode.
        backdropRenderer.render(playerCamera);
        playerCamera.lookThrough();

        if (renderingConfig.isReflectiveWater()) {
            // TODO: the isReflectiveWater() block should include content of the whole process() method.
            // TODO: Eventually the check will be removed, when node insertion will signify that the feature is enabled.
            chunkShader.activateFeature(ShaderProgramFeature.FEATURE_USE_FORWARD_LIGHTING);
            worldRenderer.renderChunks(renderQueues.chunksOpaqueReflection, ChunkMesh.RenderPhase.OPAQUE, playerCamera, WorldRendererImpl.ChunkRenderMode.REFLECTION);
            chunkShader.deactivateFeature(ShaderProgramFeature.FEATURE_USE_FORWARD_LIGHTING);
        }
        playerCamera.setReflected(false);

        PerformanceMonitor.endActivity();
    }
}
