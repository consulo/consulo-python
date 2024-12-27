package com.jetbrains.python;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkTable;
import consulo.content.bundle.SdkType;
import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public abstract class PythonMockSdk {
  @NonNls private static final String MOCK_SDK_NAME = "Mock Python SDK";

  private PythonMockSdk() {
  }

  public static Sdk findOrCreate(String version) {
    final List<Sdk> sdkList = SdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    for (Sdk sdk : sdkList) {
      if (sdk.getName().equals(MOCK_SDK_NAME + " " + version)) {
        return sdk;
      }
    }
    return create(version);
  }

  public static Sdk create(final String version) {
    final String mock_path = PythonTestUtil.getTestDataPath() + "/MockSdk" + version + "/";

    String sdkHome = new File(mock_path, "bin/python"+version).getPath();
    SdkType sdkType = PythonSdkType.getInstance();


    final Sdk sdk = new consulo.content.impl.internal.bundle.SdkImpl(SdkTable.getInstance(), MOCK_SDK_NAME + " " + version, sdkType) {
      @Override
      public String getVersionString() {
        return "Python " + version + " Mock SDK";
      }
    };
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkHome);

    File libPath = new File(mock_path, "Lib");
    if (libPath.exists()) {
      sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libPath), OrderRootType.CLASSES);
    }

    //PyUserSkeletonsUtil.addUserSkeletonsRoot(sdkModificator);

    String mock_stubs_path = mock_path + PythonSdkType.SKELETON_DIR_NAME;
    sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(mock_stubs_path), BinariesOrderRootType.getInstance());

    sdkModificator.commitChanges();

    return sdk;
  }
}
