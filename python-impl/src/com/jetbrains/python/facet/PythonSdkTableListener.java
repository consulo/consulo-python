package com.jetbrains.python.facet;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author yole
 */
public class PythonSdkTableListener implements ApplicationComponent {
  public PythonSdkTableListener(MessageBus messageBus) {
    SdkTable.Listener jdkTableListener = new SdkTable.Listener() {
      public void sdkAdded(final Sdk sdk) {
        if (sdk.getSdkType() instanceof PythonSdkType) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  addLibrary(sdk);
                }
              });
            }
          });
        }
      }

      public void sdkRemoved(final Sdk sdk) {
        if (sdk.getSdkType() instanceof PythonSdkType) {
          removeLibrary(sdk);
        }
      }

      public void sdkNameChanged(final Sdk sdk, final String previousName) {
        if (sdk.getSdkType() instanceof PythonSdkType) {
          renameLibrary(sdk, previousName);
        }
      }
    };
    messageBus.connect().subscribe(SdkTable.SDK_TABLE_TOPIC, jdkTableListener);
  }

  static Library addLibrary(Sdk sdk) {
    final LibraryTable.ModifiableModel libraryTableModel = ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel();
    final Library library = libraryTableModel.createLibrary(PythonFacet.getFacetLibraryName(sdk.getName()));
    final Library.ModifiableModel model = library.getModifiableModel();
    for (String url : sdk.getRootProvider().getUrls(OrderRootType.CLASSES)) {
      model.addRoot(url, OrderRootType.CLASSES);
      model.addRoot(url, OrderRootType.SOURCES);
    }
    model.commit();
    libraryTableModel.commit();
    return library;
  }

  private static void removeLibrary(final Sdk sdk) {
    LaterInvocator.invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run()  {
            final LibraryTable.ModifiableModel libraryTableModel = ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel();
            final Library library = libraryTableModel.getLibraryByName(PythonFacet.getFacetLibraryName(sdk.getName()));
            if (library!=null) {
              libraryTableModel.removeLibrary(library);
            }
            libraryTableModel.commit();
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }

  private static void renameLibrary(final Sdk sdk, final String previousName) {
    LaterInvocator.invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final LibraryTable.ModifiableModel libraryTableModel = ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel();
            final Library library = libraryTableModel.getLibraryByName(PythonFacet.getFacetLibraryName(previousName));
            if (library!=null){
              final Library.ModifiableModel model = library.getModifiableModel();
              model.setName(PythonFacet.getFacetLibraryName(sdk.getName()));
              model.commit();
            }
            libraryTableModel.commit();
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }

  @NotNull
  public String getComponentName() {
    return "PythonSdkTableListener";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
