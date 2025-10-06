package ee.sk.siddemo.model;

public class AnonymousRequest {

    private UserActionMock userActionMock = UserActionMock.NONE;

    public UserActionMock getUserActionMock() {
        return userActionMock;
    }

    public void setUserActionMock(UserActionMock userActionMock) {
        this.userActionMock = userActionMock;
    }
}
