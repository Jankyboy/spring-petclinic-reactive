package org.springframework.samples.petclinic.owner;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.owner.db.OwnerEntity;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive CRUD operation (WEbFlux) for entity Vet.
 *
 * @author Cedrick LUNVEN (@clunven)
 */
@RestController
@RequestMapping("/petclinic/api/owners")
@CrossOrigin(
 methods = {PUT, POST, GET, OPTIONS, DELETE, PATCH},
 maxAge = 3600,
 allowedHeaders = {"x-requested-with", "origin", "content-type", "accept"},
 origins = "*"
)
@Api(value="/api/owners", tags = {"Owners Api"})
public class OwnerReactiveController {
   
    /** Implementation of owner CRUD operations. */
    private OwnerReactiveServices ownerServices;
    
    /**
     * Injection with controller
     */
    public OwnerReactiveController(OwnerReactiveServices service) {
        this.ownerServices = service;
    }
    
    /**
     * Search owner by their lastName leveraging a secondary index.
     * Flux is returned and NOT mono as lastName is not the full primary key.
     * 
     * @param searchString
     *      input term from user
     * @return
     *      list of Owner matching the term
     */
    @GetMapping(value = "/*/lastname/{lastName}", produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value= "Search owner by their lastName", response=OwnerEntity.class)
    @ApiResponses({
        @ApiResponse(code = 200, message= "List of owners matching the lastname"), 
        @ApiResponse(code = 500, message= "Internal technical error") })
    public Flux<Owner> searchOwnersByName(@PathVariable("lastName") String searchString) {
       return ownerServices.findOwnersByName(searchString);
    }
    
    /**
     * Read all owners from database.
     *
     * @return
     *   a {@link Flux} containing {@link Vet}
     */
    @GetMapping(produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value= "Read all owners in database", response=Owner.class)
    @ApiResponses({
        @ApiResponse(code = 200, message= "List of owners (even if empty)"), 
        @ApiResponse(code = 500, message= "Internal technical error") })
    public Flux<Owner> findAllOwners() {
        return ownerServices.findAllOwners();     
    }
    
    /**
     * Retrieve owner information from its unique identifier.
     *
     * @param ownerId
     *      unique identifer as a String, to be converted in {@link UUID}.
     * @return
     *      a {@link Mono} of {@link OwnerEntity} or empty response with not found (404) code
     */
    @GetMapping(value = "/{ownerId}", produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value= "Retrieve owner information from its unique identifier", response=OwnerEntity.class)
    @ApiResponses({
        @ApiResponse(code = 200, message= "the identifier exists and related owner is returned"), 
        @ApiResponse(code = 400, message= "The uid was not a valid UUID"), 
        @ApiResponse(code = 404, message= "the identifier does not exists in DB"), 
        @ApiResponse(code = 500, message= "Internal technical error") })
    public Mono<ResponseEntity<Owner>> findOwner(@PathVariable("ownerId") @Parameter(
               required = true,example = "1ff2fbd9-bbb0-4cc1-ba37-61966aa7c5e6",
               description = "Unique identifier of a Owner") String ownerId) {
        return ownerServices.findOwnerById(ownerId)
                            .map(ResponseEntity::ok)
                            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Create a {@link OwnerEntity} when we don't know the identifier.
     *
     * @param request
     *      current http request
     * @param vetRequest
     *      fields required to create Owner (no uid)
     * @return
     *      the created owner.
     */
    @PostMapping(produces = APPLICATION_JSON_VALUE, consumes=APPLICATION_JSON_VALUE)
    @ApiOperation(value= "Create a new owner, an unique identifier is generated and returned", 
                  response=Owner.class)
    @ApiResponses({
        @ApiResponse(code = 201, message= "The owner has been created, uuid is provided in header"), 
        @ApiResponse(code = 400, message= "The JSON body was not valid"), 
        @ApiResponse(code = 500, message= "Internal technical error") })
    public Mono<ResponseEntity<Owner>> createOwner(
            UriComponentsBuilder ucb, 
            @RequestBody Owner owner) {
      Objects.requireNonNull(owner);
      owner.setId(UUID.randomUUID());
      return ownerServices.createOwner(owner)
                          .map(created -> ResponseEntity
                                  .created(buildLocation(ucb, owner.getId().toString()))
                                  .body(created));
    }
    
    private URI buildLocation(UriComponentsBuilder ucBuilder, String id) {
        return ucBuilder.path("/api/owners/{id}").buildAndExpand(id).toUri();
    }
    
    /**
     * Create or update a {@link OwnerEntity}. We do not throw exception is already exist
     * or check existence as this is the behavirous in a cassandra table to read
     * before write.
     *
     * @param ownerId
     *      unique identifier for owner
     * @param owner
     *      person as owner
     * @return
     *      the created owner.
     */
    @PutMapping(value="/{ownerId}",  
                consumes=APPLICATION_JSON_VALUE,
                produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value= "Upsert a owner (no read before write as for Cassandra)", 
                  response=Owner.class)
    @ApiResponses({
        @ApiResponse(code = 201, message= "The owner has been created, uuid is provided in header"), 
        @ApiResponse(code = 400, message= "The owner bean was not OK"), 
        @ApiResponse(code = 500, message= "Internal technical error") })
    public Mono<ResponseEntity<Owner>> upsertOwner(
            UriComponentsBuilder ucb, 
            @PathVariable("ownerId") String ownerId, 
            @RequestBody Owner owner) {
      Objects.requireNonNull(owner);
      return ownerServices.updateOwner(owner)
                          .map(created -> ResponseEntity
                                  .created(buildLocation(ucb, owner.getId().toString()))
                                  .body(created));
    }
    
    /**
     * Delete a owner from its unique identifier.
     *
     * @param vetId
     *      vetirinian identifier
     * @return
     */
    @DeleteMapping("/{ownerId}")
    @ApiOperation(value= "Delete a owner from its unique identifier", response=Void.class)
    @ApiResponses({
        @ApiResponse(code = 204, message= "The owner has been deleted"), 
        @ApiResponse(code = 400, message= "The uid was not a valid UUID"),
        @ApiResponse(code = 500, message= "Internal technical error") })
    public Mono<ResponseEntity<Void>> deleteById(@PathVariable("ownerId") @Parameter(
            required = true,example = "1ff2fbd9-bbb0-4cc1-ba37-61966aa7c5e6",
            description = "Unique identifier of a owner") String ownerId) {
        return ownerServices.deleteOwner(ownerId)
                            .map(v -> new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }
    
}
