package com.bumptech.glide.annotation.compiler;

import com.bumptech.glide.annotation.GlideType;
import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

// Links in Javadoc will work due to build setup, even though there is no direct dependency here.
/**
 * Generates classes based on Glide's annotations that configure Glide, add support for additional
 * resource types, and/or extend Glide's API.
 *
 * <p>This processor discovers all {@link AppGlideModule} and
 * {@link LibraryGlideModule} implementations that are
 * annotated with {@link com.bumptech.glide.annotation.GlideModule}. Any implementations missing the
 * annotation will be ignored.
 *
 * <p>This processor also discovers all {@link com.bumptech.glide.annotation.GlideExtension}
 * annotated classes.
 *
 * <p>Multiple classes are generated by this processor:
 * <ul>
 *   <li>For {@link LibraryGlideModule}s - A GlideIndexer class in a
 *      specific package that will later be used by the processor to discover all
 *      {@link LibraryGlideModule} classes.
 *   <li>For {@link AppGlideModule}s - A single
 *      {@link AppGlideModule} implementation
 *     ({@link com.bumptech.glide.GeneratedAppGlideModule}) that calls all
 *     {@link LibraryGlideModule}s and the
 *     original {@link AppGlideModule} in the correct order when Glide is
 *     initialized.
 *   <li>{@link com.bumptech.glide.annotation.GlideExtension}s -
 *   <ul>
 *     <li>A {@link com.bumptech.glide.request.BaseRequestOptions} implementation that contains
 *     static versions of all builder methods in the base class and both static and instance
 *     versions of methods in all {@link com.bumptech.glide.annotation.GlideExtension}s.
 *     <li>If one or more methods in one or more
 *     {@link com.bumptech.glide.annotation.GlideExtension} annotated classes are annotated with
 *     {@link GlideType}:
 *     <ul>
 *       <li>A {@link com.bumptech.glide.RequestManager} implementation containing a generated
 *       method for each method annotated with
 *       {@link GlideType}.
 *       <li>A {@link com.bumptech.glide.manager.RequestManagerRetriever.RequestManagerFactory}
 *       implementation that produces the generated {@link com.bumptech.glide.RequestManager}s.
 *       <li>A {@link com.bumptech.glide.Glide} look-alike that implements all static methods in
 *       the {@link com.bumptech.glide.Glide} singleton and returns the generated
 *       {@link com.bumptech.glide.RequestManager} implementation when appropriate.
 *     </ul>
 *   </ul>
 * </ul>
 *
 * <p>{@link AppGlideModule} implementations must only be included in
 * applications, not in libraries. There must be exactly one
 * {@link AppGlideModule} implementation per
 * Application. The {@link AppGlideModule} class is
 * used as a signal that all modules have been found and that the final merged
 * {@link com.bumptech.glide.GeneratedAppGlideModule} impl can be created.
 */
@AutoService(Processor.class)
public final class GlideAnnotationProcessor extends AbstractProcessor {
  static final boolean DEBUG = false;
  private ProcessorUtil processorUtil;
  private LibraryModuleProcessor libraryModuleProcessor;
  private AppModuleProcessor appModuleProcessor;
  private boolean isGeneratedAppGlideModuleWritten;
  private ExtensionProcessor extensionProcessor;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    processorUtil = new ProcessorUtil(processingEnvironment);
    IndexerGenerator indexerGenerator = new IndexerGenerator(processorUtil);
    libraryModuleProcessor = new LibraryModuleProcessor(processorUtil, indexerGenerator);
    appModuleProcessor = new AppModuleProcessor(processingEnvironment, processorUtil);
    extensionProcessor = new ExtensionProcessor(processorUtil, indexerGenerator);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> result = new HashSet<>();
    result.addAll(libraryModuleProcessor.getSupportedAnnotationTypes());
    result.addAll(extensionProcessor.getSupportedAnnotationTypes());
    return result;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

   /**
   * Each round we do the following:
   * <ol>
   *   <li>Find all AppGlideModules and save them to an instance variable (throw if > 1).
   *   <li>Find all LibraryGlideModules
   *   <li>For each LibraryGlideModule, write an Indexer with an Annotation with the class name.
   *   <li>If we wrote any Indexers, return and wait for the next round.
   *   <li>If we didn't write any Indexers and there is a AppGlideModule, write the
   *   GeneratedAppGlideModule. Once the GeneratedAppGlideModule is written, we expect to be
   *   finished. Any further generation of related classes will result in errors.
   * </ol>
   */
  @Override
  public boolean process(Set<? extends TypeElement> set, RoundEnvironment env) {
    processorUtil.process();
    boolean newModulesWritten = libraryModuleProcessor.processModules(set, env);
    boolean newExtensionWritten = extensionProcessor.processExtensions(set, env);
    appModuleProcessor.processModules(set, env);

    if (newExtensionWritten || newModulesWritten) {
      if (isGeneratedAppGlideModuleWritten) {
        throw new IllegalStateException("Cannot process annotations after writing AppGlideModule");
      }
      return true;
    }

    if (!isGeneratedAppGlideModuleWritten) {
      isGeneratedAppGlideModuleWritten = appModuleProcessor.maybeWriteAppModule();
    }
    return true;
  }
}
