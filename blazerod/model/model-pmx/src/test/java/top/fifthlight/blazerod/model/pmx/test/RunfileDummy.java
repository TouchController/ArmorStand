package top.fifthlight.blazerod.model.pmx.test;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;

import java.io.IOException;

@AutoBazelRepository
public class RunfileDummy {
    private static Runfiles runfiles;

    private RunfileDummy() {
    }

    public synchronized static Runfiles getRunfiles() throws IOException {
        if (runfiles == null) {
            runfiles = Runfiles.preload().withSourceRepository(AutoBazelRepository_RunfileDummy.NAME);
        }
        return runfiles;
    }
}
